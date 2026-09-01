import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class ViewComposeSourceRepairHost {
    private static final long MAX_BYTES = 1024L * 1024L;
    private static final Pattern SHA256 = Pattern.compile("^[a-f0-9]{64}$");
    private static final Pattern TEMP_NAME = Pattern.compile("^\\.viewcompose-[a-f0-9]{32}\\.tmp$");

    private ViewComposeSourceRepairHost() {}

    private static void reject(String code, String message) {
        System.out.println("ERROR\t" + code + "\t" + message.replace('\t', ' '));
        System.exit(41);
    }

    private static String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }

    private static byte[] readBounded(
        SecureDirectoryStream<Path> directory,
        Path name
    ) throws Exception {
        Set<OpenOption> options = Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
        try (SeekableByteChannel channel = directory.newByteChannel(name, options)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ByteBuffer buffer = ByteBuffer.allocate(16 * 1024);
            long total = 0;
            while (channel.read(buffer) >= 0) {
                buffer.flip();
                int count = buffer.remaining();
                total += count;
                if (total > MAX_BYTES) {
                    reject("VC-AI-SOURCE-APPLICATION-FILE-UNSAFE", "Source exceeds one MiB.");
                }
                output.write(buffer.array(), buffer.position(), count);
                buffer.clear();
            }
            return output.toByteArray();
        }
    }

    private static BasicFileAttributes attributes(
        SecureDirectoryStream<Path> directory,
        Path name
    ) throws Exception {
        BasicFileAttributeView view = directory.getFileAttributeView(
            name,
            BasicFileAttributeView.class,
            LinkOption.NOFOLLOW_LINKS
        );
        if (view == null) {
            reject("VC-AI-SOURCE-APPLICATION-FILESYSTEM-UNSUPPORTED", "No secure attributes.");
        }
        return view.readAttributes();
    }

    private static int linkCount(Path path) throws Exception {
        try {
            Object value = Files.getAttribute(path, "unix:nlink", LinkOption.NOFOLLOW_LINKS);
            return ((Number) value).intValue();
        } catch (UnsupportedOperationException error) {
            reject(
                "VC-AI-SOURCE-APPLICATION-FILESYSTEM-UNSUPPORTED",
                "The filesystem does not expose a Unix link count."
            );
            return -1;
        }
    }

    private static void requireTarget(
        SecureDirectoryStream<Path> directory,
        Path parent,
        Path name,
        String expectedHash,
        Object expectedFileKey
    ) throws Exception {
        BasicFileAttributes attributes = attributes(directory, name);
        if (!attributes.isRegularFile() || linkCount(parent.resolve(name)) != 1) {
            reject(
                "VC-AI-SOURCE-APPLICATION-FILE-UNSAFE",
                "Target is not one regular single-link file."
            );
        }
        if (expectedFileKey != null && !expectedFileKey.equals(attributes.fileKey())) {
            reject("VC-AI-SOURCE-APPLICATION-PREIMAGE-DRIFT", "Target identity changed.");
        }
        if (!sha256(readBounded(directory, name)).equals(expectedHash)) {
            reject("VC-AI-SOURCE-APPLICATION-PREIMAGE-DRIFT", "Target bytes changed.");
        }
    }

    private static void syncDirectory(Path directory) throws Exception {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException error) {
            reject(
                "VC-AI-SOURCE-APPLICATION-FILESYSTEM-UNSUPPORTED",
                "The target directory cannot be durably synchronized."
            );
        }
    }

    /** Fixed Unix JDK bridge used only when the provider omits SecureDirectoryStream. */
    private static final class NativeUnix {
        private final Class<?> unixPath = Class.forName("sun.nio.fs.UnixPath");
        private final Class<?> dispatcher = Class.forName("sun.nio.fs.UnixNativeDispatcher");
        private final Class<?> attributes = Class.forName("sun.nio.fs.UnixFileAttributes");
        private final Class<?> channels = Class.forName("sun.nio.fs.UnixChannelFactory");
        private final Method open;
        private final Method openAt;
        private final Method close;
        private final Method renameAt;
        private final Method unlinkAt;
        private final Method getByFd;
        private final Method getAt;
        private final Method isDirectory;
        private final Method isRegularFile;
        private final Method linkCount;
        private final Method fileKey;
        private final Method newFileChannel;
        private final Method newFileDescriptor;
        private final Constructor<?> fileDispatcher;
        private final Method force;
        private final int readOnly;
        private final int noFollow;

        NativeUnix() throws Exception {
            Class<?> constants = Class.forName("sun.nio.fs.UnixConstants");
            open = accessible(dispatcher.getDeclaredMethod("open", unixPath, int.class, int.class));
            openAt = accessible(dispatcher.getDeclaredMethod(
                "openat", int.class, byte[].class, int.class, int.class
            ));
            close = accessible(dispatcher.getDeclaredMethod("close", int.class));
            renameAt = accessible(dispatcher.getDeclaredMethod(
                "renameat", int.class, byte[].class, int.class, byte[].class
            ));
            unlinkAt = accessible(dispatcher.getDeclaredMethod("unlinkat", int.class, byte[].class, int.class));
            getByFd = accessible(attributes.getDeclaredMethod("get", int.class));
            getAt = accessible(attributes.getDeclaredMethod("get", int.class, unixPath, boolean.class));
            isDirectory = accessible(attributes.getDeclaredMethod("isDirectory"));
            isRegularFile = accessible(attributes.getDeclaredMethod("isRegularFile"));
            linkCount = accessible(attributes.getDeclaredMethod("nlink"));
            fileKey = accessible(attributes.getDeclaredMethod("fileKey"));
            newFileChannel = accessible(channels.getDeclaredMethod(
                "newFileChannel",
                int.class,
                unixPath,
                String.class,
                Set.class,
                int.class
            ));
            Class<?> ioUtil = Class.forName("sun.nio.ch.IOUtil");
            newFileDescriptor = accessible(ioUtil.getDeclaredMethod("newFD", int.class));
            Class<?> dispatcherClass = Class.forName("sun.nio.ch.FileDispatcherImpl");
            fileDispatcher = dispatcherClass.getDeclaredConstructor();
            fileDispatcher.setAccessible(true);
            force = accessible(dispatcherClass.getDeclaredMethod("force", FileDescriptor.class, boolean.class));
            readOnly = constant(constants, "O_RDONLY");
            noFollow = constant(constants, "O_NOFOLLOW");
        }

        private static Method accessible(Method method) {
            method.setAccessible(true);
            return method;
        }

        private static int constant(Class<?> owner, String name) throws Exception {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field.getInt(null);
        }

        private int openRoot(Path root) throws Exception {
            return (int) open.invoke(null, root, readOnly | noFollow, 0);
        }

        private int openDirectory(int parent, String name) throws Exception {
            return (int) openAt.invoke(
                null,
                parent,
                name.getBytes(StandardCharsets.UTF_8),
                readOnly | noFollow,
                0
            );
        }

        private Object attributes(int parent, String name) throws Exception {
            return getAt.invoke(null, parent, Paths.get(name), false);
        }

        private void requireDirectory(int fd) throws Exception {
            Object value = getByFd.invoke(null, fd);
            if (!(boolean) isDirectory.invoke(value)) {
                reject("VC-AI-SOURCE-APPLICATION-PATH-INVALID", "Parent is not a directory.");
            }
        }

        private String requireTarget(int parent, String name, String expectedHash, String expectedKey)
            throws Exception {
            Object before = attributes(parent, name);
            String key = fileKey.invoke(before).toString();
            if (!(boolean) isRegularFile.invoke(before) || (int) linkCount.invoke(before) != 1) {
                reject(
                    "VC-AI-SOURCE-APPLICATION-FILE-UNSAFE",
                    "Target is not one regular single-link file."
                );
            }
            if (expectedKey != null && !expectedKey.equals(key)) {
                reject("VC-AI-SOURCE-APPLICATION-PREIMAGE-DRIFT", "Target identity changed.");
            }
            byte[] bytes;
            try (FileChannel channel = channel(
                parent,
                name,
                Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS),
                0
            )) {
                bytes = readBounded(channel);
            }
            Object after = attributes(parent, name);
            if (
                !key.equals(fileKey.invoke(after).toString()) ||
                !(boolean) isRegularFile.invoke(after) ||
                (int) linkCount.invoke(after) != 1 ||
                !sha256(bytes).equals(expectedHash)
            ) {
                reject("VC-AI-SOURCE-APPLICATION-PREIMAGE-DRIFT", "Target bytes changed.");
            }
            return key;
        }

        private FileChannel channel(
            int parent,
            String name,
            Set<? extends OpenOption> options,
            int mode
        ) throws Exception {
            return (FileChannel) newFileChannel.invoke(
                null,
                parent,
                Paths.get(name),
                name,
                options,
                mode
            );
        }

        private void sync(int fd) throws Exception {
            FileDescriptor descriptor = (FileDescriptor) newFileDescriptor.invoke(null, fd);
            Object implementation = fileDispatcher.newInstance();
            int result = (int) force.invoke(implementation, descriptor, true);
            if (result < 0) {
                reject(
                    "VC-AI-SOURCE-APPLICATION-FILESYSTEM-UNSUPPORTED",
                    "The target directory cannot be durably synchronized."
                );
            }
        }

        private void closeFd(int fd) {
            try {
                close.invoke(null, fd);
            } catch (Exception ignored) {
                // The source transaction has its own durable reconciliation record.
            }
        }

        void replace(
            Path root,
            Path relative,
            String expectedHash,
            byte[] candidate,
            String candidateHash,
            String temporaryName
        ) throws Exception {
            List<Integer> descriptors = new ArrayList<>();
            int parent = -1;
            boolean temporaryCreated = false;
            try {
                int current = openRoot(root);
                descriptors.add(current);
                requireDirectory(current);
                for (int index = 0; index < relative.getNameCount() - 1; index += 1) {
                    int next = openDirectory(current, relative.getName(index).toString());
                    descriptors.add(next);
                    requireDirectory(next);
                    current = next;
                }
                parent = current;
                String targetName = relative.getFileName().toString();
                String fileKey = requireTarget(parent, targetName, expectedHash, null);
                try (FileChannel channel = channel(
                    parent,
                    temporaryName,
                    Set.of(
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE,
                        LinkOption.NOFOLLOW_LINKS
                    ),
                    0600
                )) {
                    temporaryCreated = true;
                    ByteBuffer buffer = ByteBuffer.wrap(candidate);
                    while (buffer.hasRemaining()) channel.write(buffer);
                    channel.force(true);
                }
                requireTarget(parent, targetName, expectedHash, fileKey);
                renameAt.invoke(
                    null,
                    parent,
                    temporaryName.getBytes(StandardCharsets.UTF_8),
                    parent,
                    targetName.getBytes(StandardCharsets.UTF_8)
                );
                temporaryCreated = false;
                sync(parent);
                String committedKey = requireTarget(parent, targetName, candidateHash, null);
                System.out.println("OK\t" + candidateHash + "\t" + committedKey);
            } catch (InvocationTargetException error) {
                Throwable cause = error.getCause();
                reject(
                    "VC-AI-SOURCE-APPLICATION-FILESYSTEM-UNSUPPORTED",
                    cause.getClass().getSimpleName() + ": " + cause.getMessage()
                );
            } finally {
                if (temporaryCreated && parent >= 0) {
                    try {
                        unlinkAt.invoke(
                            null,
                            parent,
                            temporaryName.getBytes(StandardCharsets.UTF_8),
                            0
                        );
                    } catch (Exception ignored) {
                        // Recovery will diagnose a retained content-addressed temporary file.
                    }
                }
                for (int index = descriptors.size() - 1; index >= 0; index -= 1) {
                    closeFd(descriptors.get(index));
                }
            }
        }
    }

    private static byte[] readBounded(FileChannel channel) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteBuffer buffer = ByteBuffer.allocate(16 * 1024);
        long total = 0;
        while (channel.read(buffer) >= 0) {
            buffer.flip();
            int count = buffer.remaining();
            total += count;
            if (total > MAX_BYTES) {
                reject("VC-AI-SOURCE-APPLICATION-FILE-UNSAFE", "Source exceeds one MiB.");
            }
            output.write(buffer.array(), buffer.position(), count);
            buffer.clear();
        }
        return output.toByteArray();
    }

    private static void replace(String[] args) throws Exception {
        if (args.length != 7) {
            reject("VC-AI-SOURCE-APPLICATION-INPUT-INVALID", "Replace arguments are invalid.");
        }
        Path root = Paths.get(args[1]);
        String relativeText = args[2];
        String expectedHash = args[3];
        Path candidatePath = Paths.get(args[4]);
        String candidateHash = args[5];
        String temporaryName = args[6];
        if (
            !root.isAbsolute() ||
            !candidatePath.isAbsolute() ||
            !SHA256.matcher(expectedHash).matches() ||
            !SHA256.matcher(candidateHash).matches() ||
            !TEMP_NAME.matcher(temporaryName).matches() ||
            relativeText.contains("\\")
        ) {
            reject("VC-AI-SOURCE-APPLICATION-INPUT-INVALID", "Replace identities are invalid.");
        }
        Path relative = Paths.get(relativeText);
        if (relative.isAbsolute() || !relative.normalize().equals(relative) || relative.getNameCount() < 1) {
            reject("VC-AI-SOURCE-APPLICATION-PATH-INVALID", "Target path is not root-relative.");
        }
        for (Path segment : relative) {
            String text = segment.toString();
            if (text.equals(".") || text.equals("..") || text.isEmpty()) {
                reject("VC-AI-SOURCE-APPLICATION-PATH-INVALID", "Target traversal is forbidden.");
            }
        }
        if (
            Files.isSymbolicLink(root) ||
            !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) ||
            !root.toRealPath(LinkOption.NOFOLLOW_LINKS).equals(root)
        ) {
            reject("VC-AI-SOURCE-APPLICATION-ROOT-DRIFT", "Project root is unsafe.");
        }
        if (
            Files.isSymbolicLink(candidatePath) ||
            !Files.isRegularFile(candidatePath, LinkOption.NOFOLLOW_LINKS) ||
            linkCount(candidatePath) != 1
        ) {
            reject("VC-AI-SOURCE-APPLICATION-RECOVERY-INVALID", "Candidate recovery file is unsafe.");
        }
        byte[] candidate = Files.readAllBytes(candidatePath);
        if (candidate.length < 1 || candidate.length > MAX_BYTES || !sha256(candidate).equals(candidateHash)) {
            reject("VC-AI-SOURCE-APPLICATION-CANDIDATE-DRIFT", "Candidate recovery bytes changed.");
        }

        List<DirectoryStream<Path>> opened = new ArrayList<>();
        Path temporary = Paths.get(temporaryName);
        SecureDirectoryStream<Path> parentStream = null;
        boolean temporaryCreated = false;
        try {
            DirectoryStream<Path> rootStream = Files.newDirectoryStream(root);
            opened.add(rootStream);
            if (!(rootStream instanceof SecureDirectoryStream<?>)) {
                rootStream.close();
                opened.clear();
                new NativeUnix().replace(
                    root,
                    relative,
                    expectedHash,
                    candidate,
                    candidateHash,
                    temporaryName
                );
                return;
            }
            @SuppressWarnings("unchecked")
            SecureDirectoryStream<Path> current = (SecureDirectoryStream<Path>) rootStream;
            Path parentPath = root;
            for (int index = 0; index < relative.getNameCount() - 1; index += 1) {
                Path segment = relative.getName(index);
                if (!attributes(current, segment).isDirectory()) {
                    reject("VC-AI-SOURCE-APPLICATION-PATH-INVALID", "Parent is not a directory.");
                }
                SecureDirectoryStream<Path> next = current.newDirectoryStream(
                    segment,
                    LinkOption.NOFOLLOW_LINKS
                );
                opened.add(next);
                current = next;
                parentPath = parentPath.resolve(segment.toString());
            }
            parentStream = current;
            Path targetName = relative.getFileName();
            BasicFileAttributes initialAttributes = attributes(parentStream, targetName);
            Object fileKey = initialAttributes.fileKey();
            requireTarget(parentStream, parentPath, targetName, expectedHash, fileKey);
            Set<OpenOption> writeOptions = Set.of(
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS
            );
            try (SeekableByteChannel channel = parentStream.newByteChannel(temporary, writeOptions)) {
                temporaryCreated = true;
                ByteBuffer buffer = ByteBuffer.wrap(candidate);
                while (buffer.hasRemaining()) channel.write(buffer);
                if (!(channel instanceof FileChannel)) {
                    reject(
                        "VC-AI-SOURCE-APPLICATION-FILESYSTEM-UNSUPPORTED",
                        "Temporary file cannot be durably synchronized."
                    );
                }
                ((FileChannel) channel).force(true);
            }
            requireTarget(parentStream, parentPath, targetName, expectedHash, fileKey);
            try {
                parentStream.move(temporary, parentStream, targetName);
                temporaryCreated = false;
            } catch (FileAlreadyExistsException error) {
                reject(
                    "VC-AI-SOURCE-APPLICATION-FILESYSTEM-UNSUPPORTED",
                    "Secure atomic replacement is unavailable."
                );
            }
            syncDirectory(parentPath);
            requireTarget(parentStream, parentPath, targetName, candidateHash, null);
            System.out.println("OK\t" + candidateHash + "\t" + attributes(parentStream, targetName).fileKey());
        } finally {
            if (temporaryCreated && parentStream != null) {
                try {
                    parentStream.deleteFile(temporary);
                } catch (Exception ignored) {
                    // Recovery will diagnose a retained, content-addressed temporary file.
                }
            }
            for (int index = opened.size() - 1; index >= 0; index -= 1) {
                try {
                    opened.get(index).close();
                } catch (Exception ignored) {
                    // The operation result is already determined by durable source state.
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || !args[0].equals("replace")) {
            reject("VC-AI-SOURCE-APPLICATION-INPUT-INVALID", "Only secure replace is supported.");
        }
        try {
            replace(args);
        } catch (Exception error) {
            reject(
                "VC-AI-SOURCE-APPLICATION-FILESYSTEM-UNSUPPORTED",
                error.getClass().getSimpleName() + ": " + error.getMessage()
            );
        }
    }
}

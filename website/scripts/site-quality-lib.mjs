import {readdir, readFile, stat} from 'node:fs/promises';
import {relative, resolve, sep} from 'node:path';

export const websiteRoot = resolve(import.meta.dirname, '..');
export const buildDir = resolve(websiteRoot, 'build');

export async function collectFiles(root, predicate = () => true) {
  const files = [];

  async function visit(directory) {
    for (const entry of await readdir(directory, {withFileTypes: true})) {
      const path = resolve(directory, entry.name);
      if (entry.isDirectory()) {
        await visit(path);
      } else if (entry.isFile() && predicate(path)) {
        files.push(path);
      }
    }
  }

  await visit(root);
  return files.sort();
}

export function relativeBuildPath(path) {
  return relative(buildDir, path).split(sep).join('/');
}

export async function totalBytes(files) {
  const sizes = await Promise.all(files.map(async (file) => (await stat(file)).size));
  return sizes.reduce((total, size) => total + size, 0);
}

export function formatMiB(bytes) {
  return `${(bytes / 1024 / 1024).toFixed(1)} MiB`;
}

export function formatKiB(bytes) {
  return `${(bytes / 1024).toFixed(0)} KiB`;
}

export async function readJson(path) {
  return JSON.parse(await readFile(path, 'utf8'));
}

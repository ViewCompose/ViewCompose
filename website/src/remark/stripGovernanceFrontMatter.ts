const GOVERNANCE_ONLY_FIELDS = [
  'schema_version',
  'document_id',
  'doc_type',
  'owner',
  'version_lane',
  'capability_ids',
  'artifact_ids',
  'sample_ids',
  'invariants',
  'evidence',
  'translation_source',
  'translation_source_hash',
  'translation_status',
] as const;

type RemarkFile = {
  data?: {
    frontMatter?: unknown;
  };
};

/**
 * Keeps Governance V2 metadata in source while excluding it from every browser page chunk.
 * Docusaurus has already resolved presentation fields such as `slug` before this remark phase.
 */
export default function stripGovernanceFrontMatter() {
  return (_tree: unknown, file: RemarkFile): void => {
    const frontMatter = file.data?.frontMatter;
    if (frontMatter === null || typeof frontMatter !== 'object' || Array.isArray(frontMatter)) {
      return;
    }
    const fields = frontMatter as Record<string, unknown>;
    for (const field of GOVERNANCE_ONLY_FIELDS) {
      delete fields[field];
    }
  };
}

type GeneratedSidebarItem = {
  type: string;
  items?: GeneratedSidebarItem[];
  link?: {type: string; id?: string} | null;
  [key: string]: unknown;
};

const catalogIndexIds = new Set(['architecture/decisions/README', 'modules/README']);

export function compactCatalogSidebars<T extends GeneratedSidebarItem>(items: T[]): T[] {
  return items.map((item) => {
    if (item.type !== 'category' || !item.items) return item;
    // The linked catalogs own the complete ordered lists. Repeating every entry in
    // global navigation multiplies their HTML and route metadata across both locales.
    if (item.link?.type === 'doc' && catalogIndexIds.has(item.link.id ?? '')) {
      return {...item, items: []} as T;
    }
    return {...item, items: compactCatalogSidebars(item.items)} as T;
  });
}

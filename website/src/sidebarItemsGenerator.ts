type GeneratedSidebarItem = {
  type: string;
  items?: GeneratedSidebarItem[];
  link?: {type: string; id?: string} | null;
  [key: string]: unknown;
};

const decisionsIndexId = 'architecture/decisions/README';

export function compactArchitectureDecisionSidebar<T extends GeneratedSidebarItem>(items: T[]): T[] {
  return items.map((item) => {
    if (item.type !== 'category' || !item.items) return item;
    if (item.link?.type === 'doc' && item.link.id === decisionsIndexId) {
      return {...item, items: []} as T;
    }
    return {...item, items: compactArchitectureDecisionSidebar(item.items)} as T;
  });
}

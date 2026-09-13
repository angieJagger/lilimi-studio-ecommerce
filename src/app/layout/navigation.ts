export interface NavigationItem {
  readonly labelKey: string;
  readonly path: string;
  readonly queryParams?: Readonly<Record<string, string>>;
}

export const shopNavigation: readonly NavigationItem[] = [
  {
    labelKey: 'nav.patterns',
    path: 'products',
    queryParams: { category: 'embroidery-patterns' },
  },
  {
    labelKey: 'nav.embroideredProducts',
    path: 'products',
    queryParams: { category: 'embroidered-products' },
  },
  {
    labelKey: 'nav.digitizing',
    path: 'services/embroidery-digitizing',
  },
  {
    labelKey: 'nav.websites',
    path: 'services/web-design',
  },
];

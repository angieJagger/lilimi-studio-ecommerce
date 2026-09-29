export interface NavigationItem {
  readonly labelKey: string;
  readonly path: string;
  readonly queryParams?: Readonly<Record<string, string>>;
  readonly fragment?: string;
}

export const shopNavigation: readonly NavigationItem[] = [
  {
    labelKey: 'nav.services',
    path: '',
    fragment: 'offer',
  },
  {
    labelKey: 'nav.shop',
    path: 'products',
  },
  {
    labelKey: 'nav.about',
    path: 'about',
  },
  {
    labelKey: 'nav.contact',
    path: 'contact',
  },
];

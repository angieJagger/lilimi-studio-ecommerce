export interface NavigationItem {
  readonly labelKey: string;
  readonly path: string;
  readonly queryParams?: Readonly<Record<string, string>>;
  readonly fragment?: string;
}

export const shopNavigation: readonly NavigationItem[] = [
  {
    labelKey: 'nav.services',
    path: 'services',
  },
  { labelKey: 'nav.projects', path: '', fragment: 'projects' },
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
  {
    labelKey: 'nav.accountLink',
    path: 'account/login',
  },
];

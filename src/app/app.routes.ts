import { Routes } from '@angular/router';
import { languageResolver } from './core/i18n/language.resolver';
import { ShopLayout } from './layout/shop-layout/shop-layout';

const shopRoutes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () => import('./features/home/home').then((m) => m.Home),
  },
  {
    path: 'products',
    loadComponent: () =>
      import('./features/products/product-list/product-list').then((m) => m.ProductList),
  },
  {
    path: 'services/embroidery-digitizing',
    loadComponent: () =>
      import('./features/services/embroidery-digitizing/embroidery-digitizing').then(
        (m) => m.EmbroideryDigitizing,
      ),
  },
  {
    path: 'services/web-design',
    loadComponent: () =>
      import('./features/services/web-design/web-design').then((m) => m.WebDesign),
  },
];

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'pl',
  },
  {
    path: 'pl',
    component: ShopLayout,
    data: { language: 'pl' },
    resolve: { translations: languageResolver },
    children: shopRoutes,
  },
  {
    path: 'en',
    component: ShopLayout,
    data: { language: 'en' },
    resolve: { translations: languageResolver },
    children: shopRoutes,
  },
];

import { RenderMode, ServerRoute } from '@angular/ssr';

export const serverRoutes: ServerRoute[] = [
  {
    path: 'pl/products',
    renderMode: RenderMode.Server,
  },
  {
    path: 'en/products',
    renderMode: RenderMode.Server,
  },
  {
    path: 'pl/products/:slug',
    renderMode: RenderMode.Server,
  },
  {
    path: 'en/products/:slug',
    renderMode: RenderMode.Server,
  },
  {
    path: 'pl/admin/login',
    renderMode: RenderMode.Client,
  },
  {
    path: 'en/admin/login',
    renderMode: RenderMode.Client,
  },
  {
    path: '**',
    renderMode: RenderMode.Prerender,
  },
];

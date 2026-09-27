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
    path: '**',
    renderMode: RenderMode.Prerender,
  },
];

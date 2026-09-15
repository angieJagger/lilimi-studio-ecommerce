import { PrerenderFallback, RenderMode, ServerRoute } from '@angular/ssr';
import { demoProducts } from './features/products/products.data';

async function getProductParams(): Promise<{ slug: string }[]> {
  return demoProducts.map((product) => ({
    slug: product.slug,
  }));
}

export const serverRoutes: ServerRoute[] = [
  {
    path: 'pl/products/:slug',
    renderMode: RenderMode.Prerender,
    getPrerenderParams: getProductParams,
    fallback: PrerenderFallback.Server,
  },
  {
    path: 'en/products/:slug',
    renderMode: RenderMode.Prerender,
    getPrerenderParams: getProductParams,
    fallback: PrerenderFallback.Server,
  },
  {
    path: '**',
    renderMode: RenderMode.Prerender,
  },
];

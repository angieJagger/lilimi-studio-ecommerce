import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { ProductCard } from '../../shared/components/product-card/product-card';
import { ProductCatalogService } from '../products/product-catalog.service';

@Component({
  imports: [TranslocoPipe, RouterLink, ProductCard],
  selector: 'app-home',
  styleUrl: './home.scss',
  templateUrl: './home.html',
})
export class Home {
  protected readonly transloco = inject(TranslocoService);

  protected readonly catalog = inject(ProductCatalogService);

  private readonly activeLanguage = toSignal(
    this.transloco.langChanges$,
    { initialValue: this.transloco.getActiveLang() },
  );

  protected readonly language = computed<'pl' | 'en'>(() =>
    this.activeLanguage() === 'en' ? 'en' : 'pl',
  );

  protected readonly featuredProducts = computed(() =>
    ['pattern-001', 'embroidered-002'].flatMap((id) => {
      const product = this.catalog.productsById().get(id);

      return product ? [product] : [];
    }),
  );
  protected readonly offerItems = [
    {
      id: 'graphic',
      titleKey: 'home.offer.graphic.title',
      descriptionKey: 'home.offer.graphic.description',
    },
    {
      id: 'web',
      titleKey: 'home.offer.web.title',
      descriptionKey: 'home.offer.web.description',
    },
  ] as const;
}

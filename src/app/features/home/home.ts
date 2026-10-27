import { primaryStudioServices, additionalStudioServices } from '../../shared/studio-services';
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

  private readonly activeLanguage = toSignal(this.transloco.langChanges$, {
    initialValue: this.transloco.getActiveLang(),
  });

  protected readonly language = computed<'pl' | 'en'>(() =>
    this.activeLanguage() === 'en' ? 'en' : 'pl',
  );

  protected readonly featuredProducts = computed(() =>
    ['pattern-001', 'embroidered-002', 'pattern-002'].flatMap((id) => {
      const product = this.catalog.productsById().get(id);

      return product ? [product] : [];
    }),
  );
  constructor() {
    // Refresh an earlier catalog snapshot when returning after product edits.
    if (this.catalog.state().status !== 'loading') {
      this.catalog.reload();
    }
  }

  protected readonly primaryServices = primaryStudioServices;
  protected readonly additionalServices = additionalStudioServices;
}

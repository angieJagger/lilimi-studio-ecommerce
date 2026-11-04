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

  protected moveSpotlight(event: PointerEvent, button: HTMLElement): void {
    if (event.pointerType === 'touch' || !window.matchMedia('(hover: hover) and (prefers-reduced-motion: no-preference)').matches) {
      return;
    }

    const bounds = button.getBoundingClientRect();
    const horizontalPosition = (event.clientX - bounds.left) / bounds.width;
    const edgeLight = Math.min(1, Math.max(0, (Math.abs(horizontalPosition - 0.5) - 0.22) / 0.18));
    button.style.setProperty('--edge-light', String(edgeLight));
    button.style.setProperty('--frame-light', String(1 - edgeLight));

    button.style.setProperty('--pointer-x', `${event.clientX - bounds.left}px`);
    button.style.setProperty('--pointer-y', `${event.clientY - bounds.top}px`);
  }
}

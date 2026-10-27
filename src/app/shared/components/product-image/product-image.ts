import { Component, computed, inject, input, signal } from '@angular/core';
import { PUBLIC_MEDIA_BASE_URL } from '../../../core/api/public-media-base-url';
import type { ProductImage as ImageModel } from '../../../features/products/product.model';

@Component({
  selector: 'app-product-image',
  template: `
    @if (image(); as photo) {
      @if (failedUrl() !== imageUrl()) {
        <img
          [src]="imageUrl()"
          [alt]="alternativeText()"
          [width]="photo.width"
          [height]="photo.height"
          [attr.loading]="eager() ? 'eager' : 'lazy'"
          decoding="async"
          (error)="failedUrl.set(imageUrl())"
        />
      } @else {
        <div class="placeholder">{{ fallbackText() }}</div>
      }
    } @else {
      <div class="placeholder">{{ fallbackText() }}</div>
    }
  `,
  styles: `
    :host {
      display: block;
      width: 100%;
      height: 100%;
      min-width: 0;
    }
    img {
      display: block;
      width: 100%;
      height: 100%;
      object-fit: contain;
    }
    .placeholder {
      display: grid;
      place-items: center;
      height: 100%;
      padding: 12px;
      box-sizing: border-box;
      text-align: center;
      color: var(--text-secondary);
      font-size: 0.875rem;
    }
  `,
})
export class ProductImage {
  readonly slug = input.required<string>();
  readonly image = input<ImageModel | null>(null);
  readonly locale = input('en');
  readonly fallbackAlt = input('');
  readonly fallbackText = input('');
  readonly eager = input(false);
  private readonly baseUrl = inject(PUBLIC_MEDIA_BASE_URL);
  protected readonly failedUrl = signal<string | null>(null);
  protected readonly imageUrl = computed(() => {
    const image = this.image();
    return image
      ? `${this.baseUrl}/products/${encodeURIComponent(this.slug())}/images/${encodeURIComponent(image.id)}/content?v=${image.version}`
      : '';
  });
  protected readonly alternativeText = computed(() => {
    const image = this.image();
    if (!image) return this.fallbackAlt();
    return (
      (this.locale().startsWith('pl')
        ? image.alt.pl || image.alt.en
        : image.alt.en || image.alt.pl) || this.fallbackAlt()
    );
  });
}

import { Component, computed, input, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ProductImage } from '../../../shared/components/product-image/product-image';
import type { ProductImage as ImageModel } from '../product.model';

@Component({
  selector: 'app-product-gallery',
  imports: [ProductImage, TranslocoPipe],
  template: `
    <section class="gallery" [attr.aria-label]="'productDetail.galleryLabel' | transloco">
      <div class="gallery__main">
        <app-product-image
          [slug]="slug()"
          [image]="selectedImage()"
          [locale]="locale()"
          [fallbackAlt]="productName()"
          [fallbackText]="'productDetail.imageUnavailable' | transloco"
          [eager]="true"
        />
      </div>
      @if (orderedImages().length > 1) {
        <ul class="gallery__thumbnails">
          @for (image of orderedImages(); track image.id) {
            <li>
              <button
                type="button"
                [class.is-selected]="selectedImage()?.id === image.id"
                [attr.aria-pressed]="selectedImage()?.id === image.id"
                [attr.aria-label]="'productDetail.selectImage' | transloco: { number: $index + 1 }"
                (click)="selection.set({ slug: slug(), id: image.id })"
              >
                <app-product-image
                  [slug]="slug()"
                  [image]="image"
                  [locale]="locale()"
                  [fallbackText]="''"
                  aria-hidden="true"
                />
              </button>
            </li>
          }
        </ul>
      }
    </section>
  `,
  styles: `
    :host {
      display: block;
      min-width: 0;
    }
    .gallery__main {
      aspect-ratio: 1;
      padding: 16px;
      border: 1px solid var(--border-brand-soft);
      border-radius: 0;
      background: var(--brand-lilac);
    }
    .gallery__thumbnails {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      padding: 0;
      margin-block: 12px 0;
      list-style: none;
    }
    .gallery__thumbnails button {
      display: block;
      width: 72px;
      height: 72px;
      padding: 4px;
      border: 2px solid transparent;
      border-radius: 0;
      background: var(--surface-default, #fff);
      cursor: pointer;
    }
    .gallery__thumbnails button.is-selected {
      border-color: var(--brand-primary, #5b21b6);
    }
    .gallery__thumbnails button:focus-visible {
      outline: 2px solid var(--brand-primary);
      outline-offset: 3px;
    }
  `,
})
export class ProductGallery {
  readonly slug = input.required<string>();
  readonly images = input<readonly ImageModel[]>([]);
  readonly locale = input('en');
  readonly productName = input('');
  protected readonly selection = signal<{ slug: string; id: string } | null>(null);
  protected readonly orderedImages = computed(() =>
    [...this.images()].sort((a, b) => Number(b.primaryImage) - Number(a.primaryImage)),
  );
  protected readonly selectedImage = computed(() => {
    const selected = this.selection();
    return (
      (selected?.slug === this.slug()
        ? this.orderedImages().find((image) => image.id === selected.id)
        : null) ??
      this.orderedImages()[0] ??
      null
    );
  });
}

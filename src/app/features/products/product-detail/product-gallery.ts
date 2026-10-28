import { Component, computed, input, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ProductImage } from '../../../shared/components/product-image/product-image';
import type { ProductImage as ImageModel } from '../product.model';

@Component({
  selector: 'app-product-gallery',
  imports: [ProductImage, TranslocoPipe],
  template: `
    <section class="gallery" [attr.aria-label]="'productDetail.galleryLabel' | transloco">
      <button class="gallery__main" type="button"
        [disabled]="!selectedImage()"
        [attr.aria-label]="'productDetail.enlargeImage' | transloco"
        aria-haspopup="dialog" (click)="zoom.showModal()">
        <app-product-image
          [slug]="slug()"
          [image]="selectedImage()"
          [locale]="locale()"
          [fallbackAlt]="productName()"
          [fallbackText]="'productDetail.imageUnavailable' | transloco"
          [eager]="true"
        />
      </button>
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
      <dialog #zoom class="gallery__zoom"
        [attr.aria-label]="'productDetail.enlargeImage' | transloco"
        (click)="closeOnBackdrop($event, zoom)">
        @if (zoom.open) {
          <div class="gallery__zoom-content">
            <button class="gallery__zoom-close" type="button" autofocus
              [attr.aria-label]="'productDetail.closeImage' | transloco"
              (click)="zoom.close()">×</button>
            <app-product-image [slug]="slug()" [image]="selectedImage()"
              [locale]="locale()" [fallbackAlt]="productName()"
              [fallbackText]="'productDetail.imageUnavailable' | transloco" [eager]="true" />
          </div>
        }
      </dialog>
    </section>
  `,
  styles: `
    :host {
      display: block;
      min-width: 0;
    }
    .gallery__main {
      aspect-ratio: 1;
      display: block;
      width: 100%;
      box-sizing: border-box;
      padding: 24px;
      cursor: zoom-in;
      border: 1px solid rgb(255 255 255 / 70%);
      border-radius: var(--radius-12);
      background: linear-gradient(145deg, #ffffff 0%, #eeeef5 100%);
      box-shadow: 0 3px 6px rgb(48 40 61 / 6%), 0 14px 28px -10px rgb(48 40 61 / 22%);
    }
    .gallery__main:disabled { cursor: default; }
    .gallery__main:focus-visible,
    .gallery__zoom-close:focus-visible {
      outline: 2px solid var(--brand-primary);
      outline-offset: 4px;
    }
    .gallery__zoom {
      max-width: none;
      max-height: none;
      width: min(92vw, 1100px);
      padding: 0;
      border: 0;
      border-radius: var(--radius-12);
      background: linear-gradient(145deg, #fff, #eeeef5);
      color: var(--text-primary);
    }
    .gallery__zoom::backdrop { background: rgb(15 13 20 / 75%); }
    .gallery__zoom-content {
      position: relative;
      height: min(85dvh, 1000px);
      padding: 48px 24px 24px;
      box-sizing: border-box;
    }
    .gallery__zoom-close {
      position: absolute;
      top: 4px;
      right: 8px;
      width: 44px;
      height: 44px;
      border: 0;
      background: transparent;
      color: inherit;
      font-size: 30px;
      cursor: pointer;
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
      border-radius: var(--radius-12);
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
  protected closeOnBackdrop(event: MouseEvent, dialog: HTMLDialogElement): void {
    if (event.target === dialog) dialog.close();
  }
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

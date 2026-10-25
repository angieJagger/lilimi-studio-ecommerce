import { HttpErrorResponse } from '@angular/common/http';
import {
  Component,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
} from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { finalize, Subscription } from 'rxjs';
import { AdminProductImageApiService } from './admin-product-image-api.service';
import type {
  AdminProductImages as Gallery,
  ProductGalleryChanges,
} from './admin-product-image.model';

interface ImageDraft {
  readonly key: string;
  readonly id: string | null;
  readonly file: File | null;
  readonly preview: string | null;
  readonly previewFailed: boolean;
  readonly width: number | null;
  readonly height: number | null;
  readonly altPl: string;
  readonly altEn: string;
  readonly primaryImage: boolean;
  readonly removed: boolean;
}

@Component({
  selector: 'app-admin-product-images',
  imports: [TranslocoPipe],
  templateUrl: './admin-product-images.html',
  styleUrl: './admin-product-images.scss',
})
export class AdminProductImages {
  readonly productId = input.required<string>();
  readonly productVersion = input.required<number>();
  readonly disabled = input(false);
  readonly draftChanged = output<void>();
  private readonly api = inject(AdminProductImageApiService);
  private readonly revision = signal(0);
  private readonly loadedVersion = signal<number | null>(null);
  private readonly loaded = signal(false);
  private readonly objectUrls = new Set<string>();
  private nextKey = 0;
  private baseline: readonly ImageDraft[] = [];
  protected readonly transloco = inject(TranslocoService);
  protected readonly drafts = signal<readonly ImageDraft[]>([]);
  protected readonly isLoading = signal(false);
  protected readonly errorKey = signal<string | null>(null);
  protected readonly validationError = signal<string | null>(null);
  protected readonly activeImages = computed(() => this.drafts().filter((image) => !image.removed));
  readonly ready = computed(() => this.loaded() && !this.isLoading() && !this.errorKey());

  constructor() {
    effect((onCleanup) => {
      const productId = this.productId();
      this.productVersion();
      this.revision();
      const requests = new Subscription();
      onCleanup(() => {
        requests.unsubscribe();
        for (const url of this.objectUrls) URL.revokeObjectURL(url);
        this.objectUrls.clear();
      });
      untracked(() => this.loadGallery(productId, requests));
    });
  }

  private loadGallery(productId: string, requests: Subscription): void {
    this.loaded.set(false);
    this.loadedVersion.set(null);
    this.drafts.set([]);
    this.baseline = [];
    this.errorKey.set(null);
    this.validationError.set(null);
    this.isLoading.set(true);

    requests.add(
      this.api
        .getImages(productId)
        .pipe(finalize(() => this.isLoading.set(false)))
        .subscribe({
          next: (gallery) => this.showGallery(productId, gallery, requests),
          error: (error: unknown) => this.errorKey.set(this.loadError(error)),
        }),
    );
  }

  private showGallery(productId: string, gallery: Gallery, requests: Subscription): void {
    this.loadedVersion.set(gallery.version);
    this.drafts.set(
      gallery.images.map((image) => ({
        key: image.id,
        id: image.id,
        file: null,
        preview: null,
        previewFailed: false,
        width: image.width,
        height: image.height,
        altPl: image.altPl,
        altEn: image.altEn,
        primaryImage: image.primaryImage,
        removed: false,
      })),
    );
    this.baseline = this.drafts();
    this.loaded.set(true);

    for (const image of gallery.images) {
      requests.add(
        this.api.getImageContent(productId, image.id).subscribe({
          next: (blob) => this.setPreview(image.id, blob),
          error: () => this.setPreviewFailed(image.id),
        }),
      );
    }
  }

  private setPreview(imageId: string, blob: Blob): void {
    const preview = this.createPreview(blob);
    this.baseline = this.baseline.map((image) =>
      image.key === imageId ? { ...image, preview } : image,
    );
    // A pending local replacement must keep its own preview.
    this.drafts.update((images) =>
      images.map((image) => (image.key === imageId && !image.file ? { ...image, preview } : image)),
    );
  }

  private setPreviewFailed(imageId: string): void {
    this.drafts.update((images) =>
      images.map((image) =>
        image.key === imageId && !image.file ? { ...image, previewFailed: true } : image,
      ),
    );
  }

  buildChanges(): ProductGalleryChanges | null {
    if (!this.ready()) return null;
    if (this.loadedVersion() !== this.productVersion()) {
      this.validationError.set('adminProductImages.errors.versionConflict');
      return null;
    }
    const files: File[] = [];
    const active = this.activeImages();
    if (
      active.length > 10 ||
      active.some((image) => image.altPl.length > 250 || image.altEn.length > 250)
    ) {
      this.validationError.set('adminProductImages.errors.altTooLong');
      return null;
    }
    if (active.length && active.filter((image) => image.primaryImage).length !== 1) return null;
    return {
      images: active.map((image) => ({
        id: image.id,
        fileIndex: image.file ? files.push(image.file) - 1 : null,
        altPl: image.altPl.trim(),
        altEn: image.altEn.trim(),
        primaryImage: image.primaryImage,
      })),
      files,
    };
  }

  resetDraft(): void {
    const keep = new Set(this.baseline.map((image) => image.preview));
    for (const url of this.objectUrls) {
      if (!keep.has(url)) {
        URL.revokeObjectURL(url);
        this.objectUrls.delete(url);
      }
    }
    this.drafts.set(this.baseline.map((image) => ({ ...image })));
    this.validationError.set(null);
  }

  protected stageFiles(input: HTMLInputElement): void {
    if (this.disabled() || !this.ready()) return;
    const files = Array.from(input.files ?? []);
    if (this.activeImages().length + files.length > 10) {
      this.validationError.set('adminProductImages.errors.limitReached');
      input.value = '';
      return;
    }
    if (files.some((file) => !this.validFile(file))) {
      input.value = '';
      return;
    }
    const noPrimary = this.activeImages().length === 0;
    this.drafts.update((items) => [
      ...items,
      ...files.map((file, index) => ({
        key: `new-${++this.nextKey}`,
        id: null,
        file,
        preview: this.createPreview(file),
        previewFailed: false,
        width: null,
        height: null,
        altPl: '',
        altEn: '',
        primaryImage: noPrimary && index === 0,
        removed: false,
      })),
    ]);
    this.validationError.set(null);
    this.draftChanged.emit();
    input.value = '';
  }

  protected replace(key: string, input: HTMLInputElement): void {
    const file = input.files?.[0];
    input.value = '';
    if (!file || this.disabled() || !this.validFile(file)) return;
    const preview = this.createPreview(file);
    this.drafts.update((items) =>
      items.map((image) =>
        image.key === key
          ? { ...image, file, preview, previewFailed: false, width: null, height: null }
          : image,
      ),
    );
    this.validationError.set(null);
    this.draftChanged.emit();
  }

  protected updateText(key: string, language: 'pl' | 'en', event: Event): void {
    if (this.disabled()) return;
    const value = (event.target as HTMLInputElement).value;
    this.drafts.update((items) =>
      items.map((image) =>
        image.key === key ? { ...image, [language === 'pl' ? 'altPl' : 'altEn']: value } : image,
      ),
    );
    this.validationError.set(null);
    this.draftChanged.emit();
  }

  protected remove(key: string): void {
    if (this.disabled()) return;
    this.drafts.update((items) => {
      const result = items.map((image) =>
        image.key === key ? { ...image, removed: true, primaryImage: false } : image,
      );
      const active = result.filter((image) => !image.removed);
      if (active.length && !active.some((image) => image.primaryImage)) {
        return result.map((image) =>
          image.key === active[0].key ? { ...image, primaryImage: true } : image,
        );
      }
      return result;
    });
    this.draftChanged.emit();
  }

  protected restore(key: string): void {
    if (this.disabled()) return;
    if (this.activeImages().length >= 10) {
      this.validationError.set('adminProductImages.errors.limitReached');
      return;
    }
    const first = !this.activeImages().length;
    this.drafts.update((items) =>
      items.map((image) =>
        image.key === key ? { ...image, removed: false, primaryImage: first } : image,
      ),
    );
    this.draftChanged.emit();
  }

  protected makePrimary(key: string): void {
    if (this.disabled()) return;
    this.drafts.update((items) =>
      items.map((image) => ({ ...image, primaryImage: !image.removed && image.key === key })),
    );
    this.draftChanged.emit();
  }

  protected move(key: string, offset: number): void {
    if (this.disabled()) return;
    const active = this.activeImages();
    const index = active.findIndex((image) => image.key === key);
    const target = active[index + offset];
    if (!target) return;
    this.drafts.update((items) => {
      const result = [...items];
      const first = result.findIndex((image) => image.key === key);
      const second = result.findIndex((image) => image.key === target.key);
      [result[first], result[second]] = [result[second], result[first]];
      return result;
    });
    this.draftChanged.emit();
  }

  protected canMove(key: string, offset: number): boolean {
    const index = this.activeImages().findIndex((image) => image.key === key);
    return index >= 0 && index + offset >= 0 && index + offset < this.activeImages().length;
  }

  protected retry(): void {
    if (!this.disabled()) this.revision.update((value) => value + 1);
  }

  protected alternativeText(image: ImageDraft): string {
    return this.transloco.getActiveLang().startsWith('pl')
      ? image.altPl || image.altEn
      : image.altEn || image.altPl;
  }

  private createPreview(blob: Blob): string {
    const url = URL.createObjectURL(blob);
    this.objectUrls.add(url);
    return url;
  }

  private validFile(file: File): boolean {
    if (!file.size || file.size > 10 * 1024 * 1024) {
      this.validationError.set('adminProductImages.errors.fileTooLarge');
      return false;
    }
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
      this.validationError.set('adminProductImages.errors.invalidImage');
      return false;
    }
    return true;
  }

  private loadError(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 401) return 'adminOrders.errors.sessionExpired';
      if (error.status === 403) return 'adminOrders.errors.accessDenied';
      if (error.status === 404) return 'adminProductImages.errors.notFound';
    }
    return 'adminProductImages.errors.loadFailed';
  }
}

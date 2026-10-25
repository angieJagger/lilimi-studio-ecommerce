export interface AdminProductImage {
  readonly id: string;
  readonly contentType: 'image/jpeg' | 'image/png' | 'image/webp';
  readonly byteSize: number;
  readonly width: number;
  readonly height: number;
  readonly sortOrder: number;
  readonly primaryImage: boolean;
  readonly altPl: string;
  readonly altEn: string;
}

export interface AdminProductImages {
  readonly productId: string;
  readonly version: number;
  readonly images: readonly AdminProductImage[];
}

export interface UploadProductImageRequest {
  readonly altPl: string;
  readonly altEn: string;
  readonly expectedVersion: number;
}

export interface UpdateProductImageTextRequest {
  readonly altPl: string;
  readonly altEn: string;
  readonly expectedVersion: number;
}

export interface ReplaceProductImageRequest {
  readonly expectedVersion: number;
}

export interface ChangePrimaryProductImageRequest {
  readonly expectedVersion: number;
}

export interface ReorderProductImagesRequest {
  readonly imageIds: readonly string[];
  readonly expectedVersion: number;
}

export interface ProductImageDraftRequest {
  readonly id: string | null;
  readonly fileIndex: number | null;
  readonly altPl: string;
  readonly altEn: string;
  readonly primaryImage: boolean;
}

export interface ProductGalleryChanges {
  readonly images: readonly ProductImageDraftRequest[];
  readonly files: readonly File[];
}

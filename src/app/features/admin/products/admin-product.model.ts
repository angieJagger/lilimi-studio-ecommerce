import type { AdminProductImages, ProductImageDraftRequest } from './admin-product-image.model';

export type AdminProductType = 'digital' | 'physical' | 'sweatshirt' | 'tshirt' | 'tote';

export interface AdminProductSummary {
  readonly id: string;
  readonly version: number;
  readonly slug: string;
  readonly productCode: string | null;
  readonly productType: AdminProductType;
  readonly active: boolean;
  readonly stockQuantity?: number | null;
  readonly madeToOrder: boolean;
  readonly personalizationAvailable: boolean;
}

export interface ShippingPackage {
  readonly lengthCm: number | null;
  readonly widthCm: number | null;
  readonly heightCm: number | null;
  readonly weightKg: number | null;
}

export interface PhysicalDraftConfiguration {
  readonly shippingPackage?: ShippingPackage | null;
  readonly category: string | null;
  readonly priceInGrosz: number | null;
  readonly variantAttributes: readonly { readonly name: string; readonly values: readonly string[] }[];
}

export interface CreateProductDraftRequest {
  readonly productType: 'digital' | 'physical';
  readonly pl: UpdateProductTranslationRequest;
  readonly en: UpdateProductTranslationRequest;
  readonly productCode?: string;
  readonly physicalConfiguration?: PhysicalDraftConfiguration;
  readonly digitalPriceInGrosz?: number;
  readonly stockQuantity?: number;
}

export interface AdminProductPage {
  readonly items: readonly AdminProductSummary[];
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
}

export interface ChangeProductVisibilityRequest {
  readonly active: boolean;
  readonly expectedVersion: number;
}

export type ProductTranslationLanguage = 'pl' | 'en';

export interface AdminProductTranslation {
  readonly language: ProductTranslationLanguage;
  readonly name: string;
  readonly description: string;
}

export interface AdminProductDetails extends AdminProductSummary {
  readonly digitalPriceInGrosz: number | null;
  readonly physicalConfiguration?: PhysicalDraftConfiguration | null;
  readonly translations: readonly AdminProductTranslation[];
}

export interface UpdateProductTranslationRequest {
  readonly name: string;
  readonly description: string;
}

export interface UpdateProductTranslationsRequest {
  readonly pl: UpdateProductTranslationRequest;
  readonly en: UpdateProductTranslationRequest;
  readonly expectedVersion: number;
}

export interface UpdateDigitalProductPriceRequest {
  readonly priceInGrosz: number;
  readonly expectedVersion: number;
}

export interface AdminGarmentVariant {
  readonly id: string;
  readonly version: number;
  readonly productId: string;
  readonly patternId: string;
  readonly embroideryOptionId: string;
  readonly fit: 'women' | 'men' | 'children';
  readonly size: string;
  readonly color: 'white' | 'navy' | 'grey' | 'black';
  readonly priceInGrosz: number;
  readonly currency: 'PLN';
  readonly stockQuantity?: number;
  readonly active: boolean;
}

export interface AdminGarmentVariantFilters {
  readonly fit?: AdminGarmentVariant['fit'];
  readonly patternId?: string;
  readonly embroideryOptionId?: string;
}

export interface AdminEmbroiderySelection {
  readonly patternId: string;
  readonly embroideryOptionId: string;
}

export interface AdminGarmentVariantPage {
  readonly items: readonly AdminGarmentVariant[];
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
}

export interface UpdateGarmentVariantRequest {
  readonly priceInGrosz: number;
  readonly stockQuantity?: number;
  readonly active: boolean;
  readonly expectedVersion: number;
}

export interface SaveProductRequest {
  readonly expectedVersion: number;
  readonly pl: UpdateProductTranslationRequest;
  readonly en: UpdateProductTranslationRequest;
  readonly digitalPriceInGrosz: number | null;
  readonly physicalPriceInGrosz?: number;
  readonly stockQuantity?: number;
  readonly images: readonly ProductImageDraftRequest[];
}

export interface SaveProductResponse {
  readonly product: AdminProductDetails;
  readonly gallery: AdminProductImages;
}

export type AdminProductType = 'digital' | 'sweatshirt' | 'tshirt' | 'tote';

export interface AdminProductSummary {
  readonly id: string;
  readonly version: number;
  readonly slug: string;
  readonly productType: AdminProductType;
  readonly active: boolean;
  readonly madeToOrder: boolean;
  readonly personalizationAvailable: boolean;
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
  readonly active: boolean;
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
  readonly active: boolean;
  readonly expectedVersion: number;
}

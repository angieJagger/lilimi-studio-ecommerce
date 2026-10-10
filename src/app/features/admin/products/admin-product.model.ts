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

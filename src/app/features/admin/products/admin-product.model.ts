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

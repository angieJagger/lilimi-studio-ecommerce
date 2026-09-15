export type ProductCategory = 'embroidery-patterns' | 'embroidered-products';

export interface LocalizedText {
  readonly pl: string;
  readonly en: string;
}

export interface Product {
  readonly id: string;
  readonly slug: string;
  readonly category: ProductCategory;
  readonly name: LocalizedText;
  readonly description: LocalizedText;
  readonly priceInGrosz: number;
  readonly priceType: 'fixed' | 'from';
}

export interface LocalizedText {
  readonly pl: string;
  readonly en: string;
}

interface ProductBase {
  readonly id: string;
  readonly slug: string;
  readonly name: LocalizedText;
  readonly description: LocalizedText;
  readonly priceInGrosz: number;
  readonly priceType: 'fixed' | 'from';
}

export interface EmbroideryPattern extends ProductBase {
  readonly category: 'embroidery-patterns';
  readonly fileFormats: readonly string[];
}

export interface EmbroideredProduct extends ProductBase {
  readonly category: 'embroidered-products';
  readonly madeToOrder: boolean;
  readonly personalizationAvailable: boolean;
}

export type Product = EmbroideryPattern | EmbroideredProduct;

export type ProductCategory = Product['category'];

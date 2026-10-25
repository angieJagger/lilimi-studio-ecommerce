export interface LocalizedText {
  readonly pl: string;
  readonly en: string;
}

export interface ProductImage {
  readonly id: string;
  readonly width: number;
  readonly height: number;
  readonly primaryImage: boolean;
  readonly alt: LocalizedText;
  readonly version: number;
}

interface ProductBase {
  readonly images?: readonly ProductImage[];
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

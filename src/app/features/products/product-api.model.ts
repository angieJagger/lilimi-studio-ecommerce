import type { LocalizedText } from './product.model';

export interface ProductApiResponse {
  readonly id: string;
  readonly slug: string;
  readonly productType: 'digital' | 'sweatshirt' | 'tshirt' | 'tote';
  readonly name: LocalizedText;
  readonly description: LocalizedText;
  readonly priceInGrosz: number;
  readonly priceType: 'fixed' | 'from';
  readonly currency: 'PLN';
  readonly fileFormats: readonly string[];
  readonly madeToOrder: boolean;
  readonly personalizationAvailable: boolean;
}

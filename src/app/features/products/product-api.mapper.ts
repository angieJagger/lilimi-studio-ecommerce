import type { ProductApiResponse } from './product-api.model';
import type { Product } from './product.model';

export function mapApiProductToProduct(response: ProductApiResponse): Product {
  const common = {
    id: response.id,
    slug: response.slug,
    name: response.name,
    description: response.description,
    priceInGrosz: response.priceInGrosz,
    priceType: response.priceType,
  };

  if (response.productType === 'digital') {
    return {
      ...common,
      category: 'embroidery-patterns',
      fileFormats: response.fileFormats,
    };
  }

  return {
    ...common,
    category: 'embroidered-products',
    madeToOrder: response.madeToOrder,
    personalizationAvailable: response.personalizationAvailable,
  };
}

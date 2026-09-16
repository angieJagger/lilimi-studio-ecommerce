import { Product } from './product.model';

export const demoProducts: readonly Product[] = [
  {
    id: 'pattern-001',
    slug: 'forest-dragon',
    category: 'embroidery-patterns',
    fileFormats: ['DST', 'PES', 'JEF'],
    name: {
      pl: 'Wzór haftu „Leśny smok”',
      en: 'Forest Dragon embroidery pattern',
    },
    description: {
      pl: 'Plik cyfrowy w formatach DST, PES i JEF.',
      en: 'Digital file in DST, PES and JEF formats.',
    },
    priceInGrosz: 2900,
    priceType: 'fixed',
  },
  {
    id: 'embroidered-001',
    slug: 'embroidered-shirt',
    category: 'embroidered-products',
    madeToOrder: true,
    personalizationAvailable: true,
    name: {
      pl: 'Koszulka z haftem',
      en: 'Embroidered T-shirt',
    },
    description: {
      pl: 'Produkt personalizowany, wykonywany na zamówienie.',
      en: 'A personalised product, made to order.',
    },
    priceInGrosz: 8900,
    priceType: 'from',
  },
  {
    id: 'pattern-002',
    slug: 'floral-monogram',
    category: 'embroidery-patterns',
    fileFormats: ['DST', 'PES', 'JEF'],
    name: {
      pl: 'Monogram kwiatowy',
      en: 'Floral monogram',
    },
    description: {
      pl: 'Plik cyfrowy w formatach DST, PES i JEF.',
      en: 'Digital file in DST, PES and JEF formats.',
    },
    priceInGrosz: 1900,
    priceType: 'fixed',
  },
  {
    id: 'embroidered-002',
    slug: 'embroidered-sweatshirt',
    category: 'embroidered-products',
    madeToOrder: true,
    personalizationAvailable: true,
    name: {
      pl: 'Bluza z haftem',
      en: 'Embroidered sweatshirt',
    },
    description: {
      pl: 'Bluza z personalizowanym haftem, wykonywana na zamówienie.',
      en: 'A sweatshirt with personalised embroidery, made to order.',
    },
    priceInGrosz: 14900,
    priceType: 'from',
  },
  {
    id: 'pattern-003',
    slug: 'butterfly-pattern',
    category: 'embroidery-patterns',
    fileFormats: ['DST', 'PES', 'JEF'],
    name: {
      pl: 'Wzór haftu „Motyl”',
      en: 'Butterfly embroidery pattern',
    },
    description: {
      pl: 'Plik cyfrowy w formatach DST, PES i JEF.',
      en: 'Digital file in DST, PES and JEF formats.',
    },
    priceInGrosz: 2400,
    priceType: 'fixed',
  },
  {
    id: 'embroidered-003',
    slug: 'embroidered-tote-bag',
    category: 'embroidered-products',
    madeToOrder: true,
    personalizationAvailable: true,
    name: {
      pl: 'Torba z haftem',
      en: 'Embroidered tote bag',
    },
    description: {
      pl: 'Torba z wybranym motywem haftu, wykonywana na zamówienie.',
      en: 'A tote bag with your chosen embroidery design, made to order.',
    },
    priceInGrosz: 6900,
    priceType: 'from',
  },
  {
    id: 'pattern-004',
    slug: 'forest-leaves-pattern',
    category: 'embroidery-patterns',
    fileFormats: ['DST', 'PES', 'JEF'],
    name: {
      pl: 'Wzór haftu „Leśne liście”',
      en: 'Forest Leaves embroidery pattern',
    },
    description: {
      pl: 'Plik cyfrowy w formatach DST, PES i JEF.',
      en: 'Digital file in DST, PES and JEF formats.',
    },
    priceInGrosz: 2200,
    priceType: 'fixed',
  },
];

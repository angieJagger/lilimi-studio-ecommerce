export type GarmentFit = 'women' | 'men' | 'children';

export type GarmentColor = 'white' | 'navy' | 'grey' | 'black';

export type EmbroideryOptionId = 'small-front' | 'large-back';

export interface EmbroideryOption {
  readonly id: EmbroideryOptionId;
  readonly widthMm: number;
  readonly heightMm: number;
  readonly placement: 'chest' | 'back';
}

export const garmentFits: readonly GarmentFit[] = ['women', 'men', 'children'];

export const garmentColors: readonly GarmentColor[] = ['white', 'navy', 'grey', 'black'];

export const garmentSizes: Readonly<Record<GarmentFit, readonly string[]>> = {
  women: ['XS', 'S', 'M', 'L', 'XL'],
  men: ['S', 'M', 'L', 'XL', 'XXL'],
  children: [
    '92',
    '98',
    '104',
    '110',
    '116',
    '122',
    '128',
    '134',
    '140',
    '146',
    '152',
    '158',
    '164',
  ],
};

export const dragonEmbroideryOptions: readonly EmbroideryOption[] = [
  {
    id: 'small-front',
    widthMm: 100,
    heightMm: 100,
    placement: 'chest',
  },
  {
    id: 'large-back',
    widthMm: 200,
    heightMm: 200,
    placement: 'back',
  },
];

export const sweatshirtPricesInGrosz: Readonly<
  Record<GarmentFit, Readonly<Record<EmbroideryOptionId, number>>>
> = {
  women: {
    'small-front': 14900,
    'large-back': 16900,
  },
  men: {
    'small-front': 14900,
    'large-back': 16900,
  },
  children: {
    'small-front': 12900,
    'large-back': 14900,
  },
};

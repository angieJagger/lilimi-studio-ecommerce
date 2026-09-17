export type DeliveryMethodId = 'dhl-courier' | 'dpd-courier' | 'inpost-courier' | 'inpost-locker';

export interface DeliveryMethod {
  readonly id: DeliveryMethodId;
  readonly kind: 'courier' | 'parcel-locker';
  readonly labelKey: string;
  readonly priceInGrosz: number | null;
}

export const deliveryMethods: readonly DeliveryMethod[] = [
  {
    id: 'dhl-courier',
    kind: 'courier',
    labelKey: 'checkout.delivery.methods.dhlCourier',
    priceInGrosz: 1200,
  },
  {
    id: 'dpd-courier',
    kind: 'courier',
    labelKey: 'checkout.delivery.methods.dpdCourier',
    priceInGrosz: 1200,
  },
  {
    id: 'inpost-courier',
    kind: 'courier',
    labelKey: 'checkout.delivery.methods.inpostCourier',
    priceInGrosz: 1200,
  },
];

import type { SweatshirtConfiguration } from '../cart/cart-item.model';
import type { DeliveryMethodId } from './delivery.model';

export interface OrderContact {
  readonly fullName: string;
  readonly email: string;
  readonly phone?: string;
}

export interface OrderAddress {
  readonly addressLine1: string;
  readonly addressLine2?: string;
  readonly postalCode: string;
  readonly city: string;
  readonly countryCode: 'PL';
}

export type CourierMethodId = Exclude<DeliveryMethodId, 'inpost-locker'>;

export type OrderDelivery =
  | {
      readonly kind: 'digital';
    }
  | {
      readonly kind: 'courier';
      readonly methodId: CourierMethodId;
      readonly address: OrderAddress;
    };

export interface DigitalOrderItem {
  readonly kind: 'digital';
  readonly productId: string;
  readonly quantity: 1;
}

export interface SweatshirtOrderItem {
  readonly kind: 'sweatshirt';
  readonly productId: string;
  readonly patternId: string;
  readonly configuration: SweatshirtConfiguration;
  readonly quantity: number;
}

export type OrderItem = DigitalOrderItem | SweatshirtOrderItem;

export interface CreateOrderRequest {
  readonly language: 'pl' | 'en';
  readonly contact: OrderContact;
  readonly delivery: OrderDelivery;
  readonly items: readonly OrderItem[];
}

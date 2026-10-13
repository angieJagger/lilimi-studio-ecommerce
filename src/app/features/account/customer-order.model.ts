export type CustomerOrderStatus = 'new' | 'processing' | 'completed' | 'cancelled';

export interface CustomerOrderSummary {
  readonly id: string;
  readonly createdAt: string;
  readonly status: CustomerOrderStatus;
  readonly currency: 'PLN';
  readonly totalInGrosz: number;
}

export interface CustomerOrderPage {
  readonly items: readonly CustomerOrderSummary[];
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
}

export interface CustomerOrderContact {
  readonly fullName: string;
  readonly email: string;
  readonly phone: string | null;
}

export interface CustomerOrderDelivery {
  readonly kind: 'digital' | 'courier';
  readonly methodId: string | null;
  readonly addressLine1: string | null;
  readonly addressLine2: string | null;
  readonly postalCode: string | null;
  readonly city: string | null;
  readonly countryCode: string | null;
}

export interface CustomerOrderItem {
  readonly kind: 'digital' | 'sweatshirt';
  readonly productId: string;
  readonly productName: string;
  readonly patternId: string | null;
  readonly patternName: string | null;
  readonly fit: string | null;
  readonly size: string | null;
  readonly color: string | null;
  readonly embroideryOptionId: string | null;
  readonly quantity: number;
  readonly unitPriceInGrosz: number;
  readonly lineTotalInGrosz: number;
}

export interface CustomerOrderDetails {
  readonly id: string;
  readonly createdAt: string;
  readonly status: CustomerOrderStatus;
  readonly language: 'pl' | 'en';
  readonly currency: 'PLN';
  readonly contact: CustomerOrderContact;
  readonly delivery: CustomerOrderDelivery;
  readonly items: readonly CustomerOrderItem[];
  readonly subtotalInGrosz: number;
  readonly deliveryPriceInGrosz: number;
  readonly totalInGrosz: number;
}

export type AdminOrderStatus = 'new' | 'processing' | 'completed' | 'cancelled';

export interface AdminOrderSummary {
  readonly id: string;
  readonly createdAt: string;
  readonly status: AdminOrderStatus;
  readonly customerFullName: string;
  readonly customerEmail: string;
  readonly currency: 'PLN';
  readonly totalInGrosz: number;
}

export interface AdminOrderPage {
  readonly items: readonly AdminOrderSummary[];
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
}

export interface AdminOrderContact {
  readonly fullName: string;
  readonly email: string;
  readonly phone: string | null;
}

export interface AdminOrderDelivery {
  readonly kind: 'digital' | 'courier';
  readonly methodId: string | null;
  readonly addressLine1: string | null;
  readonly addressLine2: string | null;
  readonly postalCode: string | null;
  readonly city: string | null;
  readonly countryCode: string | null;
}

export interface AdminOrderItem {
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

export interface AdminOrderDetails {
  readonly id: string;
  readonly version: number;
  readonly createdAt: string;
  readonly status: AdminOrderStatus;
  readonly language: 'pl' | 'en';
  readonly currency: 'PLN';
  readonly contact: AdminOrderContact;
  readonly delivery: AdminOrderDelivery;
  readonly items: readonly AdminOrderItem[];
  readonly subtotalInGrosz: number;
  readonly deliveryPriceInGrosz: number;
  readonly totalInGrosz: number;
}

export interface ChangeOrderStatusRequest {
  readonly status: AdminOrderStatus;
  readonly expectedVersion: number;
}

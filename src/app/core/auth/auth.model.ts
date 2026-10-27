export type UserRole = 'ADMIN' | 'CUSTOMER';

export interface CurrentUser {
  readonly email: string;
  readonly roles: readonly UserRole[];
  readonly firstName?: string | null;
}

export interface LoginRequest {
  readonly email: string;
  readonly password: string;
}

export interface RegisterCustomerRequest {
  readonly email: string;
  readonly password: string;
  readonly language?: 'pl' | 'en';
}
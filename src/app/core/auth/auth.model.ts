export type UserRole = 'ADMIN' | 'CUSTOMER';

export interface CurrentUser {
  readonly email: string;
  readonly roles: readonly UserRole[];
}

export interface LoginRequest {
  readonly email: string;
  readonly password: string;
}

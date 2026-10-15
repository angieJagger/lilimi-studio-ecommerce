export interface CustomerProfile {
  email: string;
  firstName: string | null;
  lastName: string | null;
  phone: string | null;
  addressLine1: string | null;
  addressLine2: string | null;
  postalCode: string | null;
  city: string | null;
  countryCode: string | null;
  version: number | null;
}

export type UpdateCustomerProfileRequest = Omit<CustomerProfile, 'email'>;

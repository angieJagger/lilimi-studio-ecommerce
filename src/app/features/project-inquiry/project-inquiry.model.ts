export type ProjectType = 'embroideredProduct' | 'digitizing' | 'website' | 'other';

export interface CreateInquiryRequest {
  readonly language: 'pl' | 'en';
  readonly name: string;
  readonly email: string;
  readonly projectType: ProjectType;
  readonly description: string;
  readonly inspirationUrl: string | null;
  readonly productId: string | null;
}

export interface CreateInquiryResponse {
  readonly id: string;
  readonly createdAt: string;
  readonly status: 'new';
}

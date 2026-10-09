import type { ProjectType } from '../../project-inquiry/project-inquiry.model';

export type InquiryStatus = 'new' | 'in_progress' | 'answered' | 'closed';

export interface AdminInquirySummary {
  readonly id: string;
  readonly createdAt: string;
  readonly status: InquiryStatus;
  readonly name: string;
  readonly email: string;
  readonly projectType: ProjectType;
  readonly productId: string | null;
}

export interface AdminInquiryPage {
  readonly items: readonly AdminInquirySummary[];
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
}

export interface AdminInquiryDetails extends AdminInquirySummary {
  readonly language: 'pl' | 'en';
  readonly description: string;
  readonly inspirationUrl: string | null;
}

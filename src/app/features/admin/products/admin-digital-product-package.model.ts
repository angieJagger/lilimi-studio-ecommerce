export interface AdminDigitalProductPackage {
  readonly id: string;
  readonly productId: string;
  readonly packageVersion: number;
  readonly originalFilename: string;
  readonly byteSize: number;
  readonly sha256: string;
  readonly currentPackage: boolean;
  readonly createdAt: string;
}

import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

@Component({
  selector: 'app-services-overview',
  imports: [RouterLink, TranslocoPipe],
  templateUrl: './services-overview.html',
  styleUrl: './services-overview.scss',
})
export class ServicesOverview {
  protected readonly transloco = inject(TranslocoService);

  protected readonly services = [
    'uxUi',
    'webDevelopment',
    'wordpress',
    'graphicDesign',
    'embroideryDigitizing',
  ] as const;

  protected readonly inquiryTypes = {
    uxUi: 'uxUi',
    webDevelopment: 'website',
    wordpress: 'wordpress',
    graphicDesign: 'graphicDesign',
    embroideryDigitizing: 'digitizing',
  } as const;
}

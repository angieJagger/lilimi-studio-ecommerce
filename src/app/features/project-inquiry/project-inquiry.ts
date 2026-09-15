import { Component, computed, inject, signal } from '@angular/core';
import { email, form, FormField, required, validate } from '@angular/forms/signals';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { demoProducts } from '../products/products.data';

type ProjectType = 'embroideredProduct' | 'digitizing' | 'website' | 'other';

interface ProjectInquiryModel {
  name: string;
  email: string;
  projectType: ProjectType | '';
  description: string;
  inspirationUrl: string;
}
@Component({
  imports: [TranslocoPipe, FormField],
  selector: 'app-project-inquiry',
  styleUrl: './project-inquiry.scss',
  templateUrl: './project-inquiry.html',
})
export class ProjectInquiry {

  private readonly route = inject(ActivatedRoute);
  private readonly transloco = inject(TranslocoService);
  
  private readonly queryParams = toSignal(this.route.queryParamMap, {
    initialValue: this.route.snapshot.queryParamMap,
  });
  
  private readonly activeLanguage = toSignal(this.transloco.langChanges$, {
    initialValue: this.transloco.getActiveLang(),
  });
  
  protected readonly language = computed<'pl' | 'en'>(() =>
    this.activeLanguage() === 'en' ? 'en' : 'pl',
  );
  
  protected readonly selectedProduct = computed(() => {
    const slug = this.queryParams().get('product');
  
    return demoProducts.find((product) => product.slug === slug);
  });

  protected readonly projectTypes: readonly ProjectType[] = [
    'embroideredProduct',
    'digitizing',
    'website',
    'other',
  ];

  protected readonly inquiryModel = signal<ProjectInquiryModel>({
    name: '',
    email: '',
    projectType: '',
    description: '',
    inspirationUrl: '',
  });

  protected readonly inquiryForm = form(this.inquiryModel, (path) => {
    required(path.name, {
      message: 'projectInquiry.errors.nameRequired',
    });

    required(path.email, {
      message: 'projectInquiry.errors.emailRequired',
    });

    email(path.email, {
      message: 'projectInquiry.errors.emailInvalid',
    });

    required(path.projectType, {
      message: 'projectInquiry.errors.projectTypeRequired',
    });

    required(path.description, {
      message: 'projectInquiry.errors.descriptionRequired',
    });

    validate(path.inspirationUrl, ({ value }) => {
      const input = value().trim();

      if (input === '') {
        return undefined;
      }

      const error = {
        kind: 'invalidUrl',
        message: 'projectInquiry.errors.inspirationUrlInvalid',
      };

      if (!/^https?:\/\//i.test(input)) {
        return error;
      }

      try {
        const url = new URL(input);

        return url.hostname ? undefined : error;
      } catch {
        return error;
      }
    });
  });

  protected readonly validationPassed = signal(false);

  protected onSubmit(event: Event): void {
    event.preventDefault();
    this.validationPassed.set(false);
    this.inquiryForm().markAsTouched();

    if (!this.inquiryForm().valid()) {
      const firstError = this.inquiryForm().errorSummary()[0];
      firstError?.fieldTree().focusBoundControl();
      return;
    }

    this.validationPassed.set(true);
  }
}

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProjectInquiry } from './project-inquiry';
import { getTranslocoTestingModule } from '../../testing/transloco-testing';

describe('ProjectInquiry', () => {
  let component: ProjectInquiry;
  let fixture: ComponentFixture<ProjectInquiry>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProjectInquiry, getTranslocoTestingModule()],
    }).compileComponents();

    fixture = TestBed.createComponent(ProjectInquiry);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  async function fillRequiredFields(): Promise<void> {
    const element = fixture.nativeElement as HTMLElement;

    const values = {
      'inquiry-name': 'Anna',
      'inquiry-email': 'anna@example.com',
      'inquiry-project-type': 'website',
      'inquiry-description': 'Potrzebuję strony dla pracowni haftu.',
    };

    for (const [id, value] of Object.entries(values)) {
      const control = element.querySelector<
        HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement
      >(`#${id}`)!;

      control.value = value;
      control.dispatchEvent(new Event('input', { bubbles: true }));

      if (control.tagName === 'SELECT') {
        control.dispatchEvent(new Event('change', { bubbles: true }));
      }
    }

    await fixture.whenStable();
  }

  it('should show required errors and focus the first invalid field on submit', async () => {
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('.form-field__errors')).toBeNull();

    element.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    await fixture.whenStable();

    const invalidFields = element.querySelectorAll('[aria-invalid="true"]');

    expect(invalidFields.length).toBe(4);
    expect(document.activeElement).toBe(element.querySelector('#inquiry-name'));
  });

  it('should accept valid required fields without an inspiration URL', async () => {
    await fillRequiredFields();

    const element = fixture.nativeElement as HTMLElement;

    element.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    await fixture.whenStable();

    expect(element.querySelector('[aria-invalid="true"]')).toBeNull();
    expect(element.querySelector('[role="status"]')?.textContent).toContain(
      'zapytanie nie zostało wysłane',
    );
  });

  it('should reject an invalid inspiration URL', async () => {
    await fillRequiredFields();

    const element = fixture.nativeElement as HTMLElement;
    const inspiration = element.querySelector<HTMLInputElement>('#inquiry-inspiration')!;

    inspiration.value = 'abc';
    inspiration.dispatchEvent(new Event('input', { bubbles: true }));
    await fixture.whenStable();

    element.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    await fixture.whenStable();

    expect(inspiration.getAttribute('aria-invalid')).toBe('true');
    expect(element.querySelector('#inquiry-inspiration-errors')).not.toBeNull();
    expect(element.querySelector('[role="status"]')?.textContent?.trim()).toBe('');
  });
});

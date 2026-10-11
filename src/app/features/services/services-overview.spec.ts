import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';
import { getTranslocoTestingModule } from '../../testing/transloco-testing';
import { ServicesOverview } from './services-overview';

describe('ServicesOverview', () => {
  let fixture: ComponentFixture<ServicesOverview>;
  let element: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ServicesOverview, getTranslocoTestingModule()],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(ServicesOverview);
    element = fixture.nativeElement;

    await fixture.whenStable();
  });

  it('should display all five agreed service categories', () => {
    const headings = Array.from(element.querySelectorAll('.service-card h2'), (heading) =>
      heading.textContent?.trim(),
    );

    expect(headings).toEqual([
      'UX/UI i Figma',
      'Web development',
      'WordPress i WooCommerce',
      'Grafika',
      'Digitalizacja haftu',
    ]);

    expect(element.querySelectorAll('.service-card li')).toHaveLength(20);
  });

  it('should link every service to the inquiry form in the current language', async () => {
    const links = () =>
      Array.from(element.querySelectorAll<HTMLAnchorElement>('.service-card__action'));

    expect(links()).toHaveLength(5);

    for (const link of links()) {
      expect(link.getAttribute('href')).toBe('/pl/project-inquiry');
    }

    TestBed.inject(TranslocoService).setActiveLang('en');

    await fixture.whenStable();

    for (const link of links()) {
      expect(link.getAttribute('href')).toBe('/en/project-inquiry');
    }

    expect(element.querySelector('h1')?.textContent).toContain('Lilimi Studio services');
  });
});

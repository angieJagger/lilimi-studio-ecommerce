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

  it('should group the main services and retain the additional disciplines', () => {
    const headings = Array.from(element.querySelectorAll('.service-row h2'), (heading) =>
      heading.textContent?.trim(),
    );

    expect(headings).toEqual([
      'Strony internetowe i WordPress',
      'Sklepy internetowe',
      'Poprawki i utrzymanie',
    ]);

    expect(element.querySelectorAll('.service-row__included li')).toHaveLength(12);
    expect(element.querySelector('.service-row__included li')?.textContent).toContain(
      'Układ dopasowany do telefonu, tabletu i komputera',
    );
    expect(element.textContent).not.toContain('studioServices.');
    expect(element.querySelectorAll('.studio-extras__card')).toHaveLength(3);
  });

  it('should pass the correct project type in both languages', async () => {
    const projectTypes = ['website', 'other', 'other', 'uxUi', 'graphicDesign', 'digitizing'];

    const links = () =>
      Array.from(
        element.querySelectorAll<HTMLAnchorElement>(
          '.service-card__action, .studio-extras__card a',
        ),
      );

    expect(links()).toHaveLength(6);

    links().forEach((link, index) => {
      expect(link.getAttribute('href')).toBe(
        `/pl/project-inquiry?projectType=${projectTypes[index]}`,
      );
    });

    TestBed.inject(TranslocoService).setActiveLang('en');

    await fixture.whenStable();

    links().forEach((link, index) => {
      expect(link.getAttribute('href')).toBe(
        `/en/project-inquiry?projectType=${projectTypes[index]}`,
      );
    });

    expect(element.querySelector('h1')?.textContent).toContain(
      'Everything needed to get your business online',
    );
  });
});

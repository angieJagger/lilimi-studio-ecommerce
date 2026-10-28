import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Contact } from './contact';
import { provideRouter } from '@angular/router';
import { getTranslocoTestingModule } from '../../testing/transloco-testing';

describe('Contact', () => {
  let component: Contact;
  let fixture: ComponentFixture<Contact>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Contact, getTranslocoTestingModule()],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(Contact);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should provide a public email and localized inquiry link without the demo notice', () => {
    const email = fixture.nativeElement.querySelector('.contact__email') as HTMLAnchorElement;
    expect(email.getAttribute('href')).toBe('mailto:contact@lilimistudio.com');
    expect(email.textContent).toContain('contact@lilimistudio.com');
    expect(fixture.nativeElement.querySelector('.contact__cta').getAttribute('href')).toBe('/pl/project-inquiry');
    expect(fixture.nativeElement.querySelector('#contact-demo-notice')).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('gmail.com');
  });
  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

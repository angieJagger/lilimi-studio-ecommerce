import { provideRouter } from '@angular/router';
import { getTranslocoTestingModule } from '../../../testing/transloco-testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EmbroideryDigitizing } from './embroidery-digitizing';

describe('EmbroideryDigitizing', () => {
  let component: EmbroideryDigitizing;
  let fixture: ComponentFixture<EmbroideryDigitizing>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EmbroideryDigitizing, getTranslocoTestingModule()],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(EmbroideryDigitizing);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SweatshirtConfigurator } from './sweatshirt-configurator';
import { provideRouter } from '@angular/router';
import { getTranslocoTestingModule } from '../../../testing/transloco-testing';

describe('SweatshirtConfigurator', () => {
  let component: SweatshirtConfigurator;
  let fixture: ComponentFixture<SweatshirtConfigurator>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SweatshirtConfigurator, getTranslocoTestingModule()],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(SweatshirtConfigurator);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

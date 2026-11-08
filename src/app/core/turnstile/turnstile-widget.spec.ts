import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { getTranslocoTestingModule } from '../../testing/transloco-testing';
import { TurnstileApiService } from './turnstile-api.service';
import { TurnstileScriptService } from './turnstile-script.service';
import { TurnstileWidget } from './turnstile-widget';
import type { TurnstileRenderOptions } from './turnstile.types';

describe('TurnstileWidget', () => {
  let options: TurnstileRenderOptions;

  const configuration = {
    getConfiguration: vi.fn(),
  };

  const api = {
    ready: vi.fn(),
    render: vi.fn(),
    reset: vi.fn(),
    remove: vi.fn(),
  };

  const script = {
    load: vi.fn(),
  };

  beforeEach(async () => {
    vi.resetAllMocks();

    configuration.getConfiguration.mockReturnValue(
      of({
        enabled: true,
        siteKey: 'test-site-key',
      }),
    );

    api.render.mockImplementation((_container: HTMLElement, value: TurnstileRenderOptions) => {
      options = value;
      return 'test-widget';
    });

    script.load.mockResolvedValue(api);

    await TestBed.configureTestingModule({
      imports: [TurnstileWidget, getTranslocoTestingModule()],
      providers: [
        { provide: TurnstileApiService, useValue: configuration },
        { provide: TurnstileScriptService, useValue: script },
      ],
    }).compileComponents();
  });

  async function createWidget() {
    const fixture = TestBed.createComponent(TurnstileWidget);
    fixture.componentRef.setInput('action', 'project-inquiry');

    const stateChanged = vi.fn();
    fixture.componentInstance.stateChange.subscribe(stateChanged);

    await fixture.whenStable();

    return { fixture, stateChanged };
  }

  it('should remain blocked until verification succeeds', async () => {
    const { stateChanged } = await createWidget();

    expect(api.render).toHaveBeenCalledTimes(1);
    expect(options.action).toBe('project-inquiry');
    expect(stateChanged).toHaveBeenLastCalledWith({
      ready: false,
      token: null,
    });

    options.callback('verified-token');

    expect(stateChanged).toHaveBeenLastCalledWith({
      ready: true,
      token: 'verified-token',
    });
  });

  it('should block submission again when the token expires', async () => {
    const { stateChanged } = await createWidget();

    options.callback('verified-token');
    options['expired-callback']();

    expect(stateChanged).toHaveBeenLastCalledWith({
      ready: false,
      token: null,
    });
  });

  it('should block submission when verification fails', async () => {
    const { fixture, stateChanged } = await createWidget();

    options['error-callback']('test-error');
    await fixture.whenStable();

    expect(stateChanged).toHaveBeenLastCalledWith({
      ready: false,
      token: null,
    });

    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('[role="alert"]')).not.toBeNull();
  });

  it('should clear the token when verification is reset', async () => {
    const { fixture, stateChanged } = await createWidget();

    options.callback('verified-token');

    fixture.componentInstance.reset();

    expect(api.reset).toHaveBeenCalledExactlyOnceWith('test-widget');
    expect(stateChanged).toHaveBeenLastCalledWith({
      ready: false,
      token: null,
    });
  });

  it('should remove the widget and ignore callbacks after destruction', async () => {
    const { fixture, stateChanged } = await createWidget();

    fixture.destroy();
    stateChanged.mockClear();

    options.callback('late-token');

    expect(api.remove).toHaveBeenCalledExactlyOnceWith('test-widget');
    expect(stateChanged).not.toHaveBeenCalled();
  });

  it('should allow submission without loading the script when protection is disabled', async () => {
    configuration.getConfiguration.mockReturnValue(
      of({
        enabled: false,
        siteKey: null,
      }),
    );

    const { stateChanged } = await createWidget();

    expect(script.load).not.toHaveBeenCalled();
    expect(api.render).not.toHaveBeenCalled();
    expect(stateChanged).toHaveBeenLastCalledWith({
      ready: true,
      token: null,
    });
  });

  it('should keep submission blocked when the script cannot be loaded', async () => {
    script.load.mockRejectedValue(new Error('Script unavailable'));

    const { fixture, stateChanged } = await createWidget();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(api.render).not.toHaveBeenCalled();
    expect(stateChanged).toHaveBeenLastCalledWith({
      ready: false,
      token: null,
    });

    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('[role="alert"]')).not.toBeNull();
  });

  it('should retry loading verification after a script failure', async () => {
    script.load.mockRejectedValueOnce(new Error('Script unavailable'));

    const { fixture, stateChanged } = await createWidget();

    await fixture.whenStable();
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    const retryButton = element.querySelector<HTMLButtonElement>('button[type="button"]');

    expect(retryButton).not.toBeNull();

    retryButton!.click();

    await fixture.whenStable();
    fixture.detectChanges();

    expect(script.load).toHaveBeenCalledTimes(2);
    expect(api.render).toHaveBeenCalledTimes(1);
    expect(element.querySelector('[role="alert"]')).toBeNull();

    options.callback('token-after-retry');

    expect(stateChanged).toHaveBeenLastCalledWith({
      ready: true,
      token: 'token-after-retry',
    });
  });
});

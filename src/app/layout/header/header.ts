import { afterNextRender, Component, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { CartService } from '../../features/cart/cart.service';
import { LanguageSwitcher } from '../language-switcher/language-switcher';
import { MobileMenu } from '../mobile-menu/mobile-menu';
import { shopNavigation } from '../navigation';

@Component({
  imports: [RouterLink, TranslocoPipe, MobileMenu, LanguageSwitcher],
  selector: 'app-header',
  styleUrl: './header.scss',
  templateUrl: './header.html',
})
export class Header {
  private readonly destroyRef = inject(DestroyRef);

  protected readonly transloco = inject(TranslocoService);
  protected readonly cart = inject(CartService);
  protected readonly session = inject(AuthSessionService);

  protected readonly shopNavigation = shopNavigation.filter(
    (item) => item.path !== 'account/login',
  );

  constructor() {
    afterNextRender(() => {
      this.session
        .refresh()
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          error: () => {
            // AuthSessionService clears the local session on failure.
            // Keep the public header available with the sign-in link.
          },
        });
    });
  }
}

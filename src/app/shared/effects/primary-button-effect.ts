import { DOCUMENT } from '@angular/common';
import { afterNextRender, DestroyRef, inject, Injectable, NgZone } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class PrimaryButtonEffect {
  private readonly document = inject(DOCUMENT);
  private readonly zone = inject(NgZone);
  private readonly destroyRef = inject(DestroyRef);

  constructor() {
    afterNextRender(() => {
      this.zone.runOutsideAngular(() => this.listen());
    });
  }

  private listen(): void {
    const view = this.document.defaultView;

    if (!view || typeof view.matchMedia !== 'function') {
      return;
    }

    const motion = view.matchMedia(
      '(hover: hover) and (pointer: fine) and (prefers-reduced-motion: no-preference)',
    );
    const decorations = new Map<HTMLElement, HTMLElement>();
    let active: HTMLElement | null = null;
    let frame: number | null = null;
    let lastTime = 0;
    let x = 0;
    let y = 0;
    let radius = 90;
    let targetX = 0;
    let targetY = 0;
    let targetRadius = 90;

    const stop = (): void => {
      if (frame !== null) {
        view.cancelAnimationFrame(frame);
        frame = null;
      }

      active?.classList.remove('primary-light-active');
      active = null;
      lastTime = 0;
    };

    const animate = (time: number): void => {
      frame = null;

      if (!active?.isConnected || !motion.matches || !this.isEnabled(active)) {
        stop();
        return;
      }

      const elapsed = lastTime ? Math.min(50, time - lastTime) : 16.67;
      lastTime = time;
      const positionBlend = 1 - Math.pow(0.85, elapsed / 16.67);
      const radiusBlend = 1 - Math.pow(0.88, elapsed / 16.67);
      x += (targetX - x) * positionBlend;
      y += (targetY - y) * positionBlend;
      radius += (targetRadius - radius) * radiusBlend;

      active.style.setProperty('--primary-light-x', `${x}px`);
      active.style.setProperty('--primary-light-y', `${y}px`);
      active.style.setProperty('--primary-light-radius', `${radius}px`);

      if (Math.abs(targetX - x) + Math.abs(targetY - y) +
          Math.abs(targetRadius - radius) > 0.3) {
        frame = view.requestAnimationFrame(animate);
      } else {
        lastTime = 0;
      }
    };

    const move = (event: PointerEvent): void => {
      if (event.pointerType === 'touch' || !motion.matches) {
        stop();
        return;
      }

      // Prefer the actual target when buttons are close together.
      const target = event.target instanceof Element
        ? event.target.closest<HTMLElement>('.button--primary')
        : null;
      let candidate = target && this.isEnabled(target) ? target : null;

      if (!candidate) {
        let nearestDistance = 45;

        for (const button of this.document.querySelectorAll<HTMLElement>('.button--primary')) {
          if (!this.isEnabled(button)) {
            continue;
          }

          const bounds = button.getBoundingClientRect();

          if (bounds.width === 0 || bounds.height === 0) {
            continue;
          }

          const dx = Math.max(bounds.left - event.clientX, 0, event.clientX - bounds.right);
          const dy = Math.max(bounds.top - event.clientY, 0, event.clientY - bounds.bottom);
          const distance = Math.hypot(dx, dy);

          if (distance < nearestDistance) {
            nearestDistance = distance;
            candidate = button;
          }
        }
      }

      if (!candidate) {
        stop();
        return;
      }

      const bounds = candidate.getBoundingClientRect();
      targetX = event.clientX - bounds.left;
      targetY = event.clientY - bounds.top;

      const dx = (targetX - bounds.width / 2) / (bounds.width / 2);
      const dy = (targetY - bounds.height / 2) / (bounds.height / 2);
      const distanceFromCenter = Math.min(1, Math.hypot(dx, dy));
      const inside = targetX >= 0 && targetX <= bounds.width &&
        targetY >= 0 && targetY <= bounds.height;

      // The same continuous radius change as in the reference, scaled to the button.
      targetRadius = inside
        ? Math.max(60, bounds.width * 0.45) +
          (1 - distanceFromCenter) * Math.max(120, bounds.width * 0.8)
        : 70;

      if (active !== candidate) {
        stop();
        active = candidate;
        x = targetX;
        y = targetY;
        radius = 70;

        // Remove decorations belonging to views that have been destroyed.
        for (const [button, decoration] of decorations) {
          if (!button.isConnected) {
            decoration.remove();
            decorations.delete(button);
          }
        }

        if (!decorations.has(candidate)) {
          const decoration = this.createStars(candidate);
          decorations.set(candidate, decoration);
        }

        active.style.setProperty('--primary-light-x', `${x}px`);
        active.style.setProperty('--primary-light-y', `${y}px`);
        active.style.setProperty('--primary-light-radius', `${radius}px`);
        active.classList.add('primary-light-active');
      }

      if (frame === null) {
        frame = view.requestAnimationFrame(animate);
      }
    };

    const leave = (event: PointerEvent): void => {
      if (!event.relatedTarget) {
        stop();
      }
    };

    this.document.addEventListener('pointermove', move, { passive: true });
    this.document.addEventListener('pointerout', leave, { passive: true });
    view.addEventListener('blur', stop);
    view.addEventListener('scroll', stop, { passive: true });
    motion.addEventListener('change', stop);

    this.destroyRef.onDestroy(() => {
      stop();
      this.document.removeEventListener('pointermove', move);
      this.document.removeEventListener('pointerout', leave);
      view.removeEventListener('blur', stop);
      view.removeEventListener('scroll', stop);
      motion.removeEventListener('change', stop);

      for (const decoration of decorations.values()) {
        decoration.remove();
      }
    });
  }

  private isEnabled(button: HTMLElement): boolean {
    return !button.matches(':disabled, [aria-disabled="true"], [hidden]') &&
      !button.closest('[inert], fieldset:disabled');
  }

  private createStars(button: HTMLElement): HTMLElement {
    const decoration = this.document.createElement('span');
    decoration.className = 'primary-button-stars';
    decoration.setAttribute('aria-hidden', 'true');

    for (let index = 0; index < 18; index++) {
      const star = this.document.createElement('span');
      const angle = Math.random() * Math.PI * 2;
      const distance = Math.sqrt(Math.random()) * 40;
      const x = Math.cos(angle) * distance;
      const y = Math.sin(angle) * distance;

      star.className = 'primary-button-star' +
        (Math.random() < 0.3 ? ' primary-button-star--tint' : '');
      star.style.setProperty('--star-x', `${x}px`);
      star.style.setProperty('--star-y', `${y}px`);
      star.style.setProperty('--star-size', `${Math.random() < 0.8 ? 1.5 : 2.5}px`);
      star.style.setProperty('--star-duration', `${2 + Math.random() * 4}s`);
      star.style.setProperty('--star-drift-duration', `${10 + Math.random() * 12}s`);
      star.style.setProperty('--star-delay', `${-Math.random() * 6}s`);
      star.style.setProperty('--star-dx', `${(Math.random() - 0.5) * 20}px`);
      star.style.setProperty('--star-dy', `${(Math.random() - 0.5) * 20}px`);
      decoration.appendChild(star);
    }

    button.appendChild(decoration);
    return decoration;
  }
}

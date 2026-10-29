/**
 * Single-flight mutex for token refresh serialization.
 * Ensures exactly one refresh request runs when multiple queries fail with 401 in parallel.
 */
export class RefreshMutex {
  private _isLocked = false;
  private _waiters: (() => void)[] = [];

  public isLocked(): boolean {
    return this._isLocked;
  }

  public async acquire(): Promise<void> {
    if (!this._isLocked) {
      this._isLocked = true;
      return;
    }

    return new Promise<void>((resolve) => {
      this._waiters.push(resolve);
    });
  }

  public release(): void {
    this._isLocked = false;
    const waiters = this._waiters.slice();
    this._waiters = [];
    for (const resolve of waiters) {
      resolve();
    }
  }

  public async waitForUnlock(): Promise<void> {
    if (!this._isLocked) {
      return;
    }

    return new Promise<void>((resolve) => {
      this._waiters.push(resolve);
    });
  }
}

export const refreshMutex = new RefreshMutex();

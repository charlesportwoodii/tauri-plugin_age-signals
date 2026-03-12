<script lang="ts">
  import { checkAgeRange } from 'tauri-plugin-age-signals'
  import type { AgeSignalsError } from 'tauri-plugin-age-signals'

  const MINIMUM_AGE = 13;

  type AgeCheckState = 'idle' | 'checking' | 'eligible' | 'not_applicable' | 'below_age' | 'error';

  let state = $state<AgeCheckState>('idle');
  let errorMessage = $state('');
  let errorType = $state('');
  let isLoading = $derived(state === 'checking');

  let statusClass = $derived(({
    idle: 'status-idle',
    checking: 'status-idle',
    eligible: 'status-eligible',
    not_applicable: 'status-not-applicable',
    below_age: 'status-below-age',
    error: 'status-error',
  } as Record<AgeCheckState, string>)[state]);

  let statusText = $derived(({
    idle: 'Not checked yet',
    checking: 'Checking...',
    eligible: `Eligible (${MINIMUM_AGE}+)`,
    not_applicable: 'Not applicable in this region',
    below_age: `Below minimum age (${MINIMUM_AGE})`,
    error: `Error: ${errorType}`,
  } as Record<AgeCheckState, string>)[state]);

  async function performAgeCheck() {
    state = 'checking';
    errorMessage = '';
    errorType = '';

    try {
      const result = await checkAgeRange(MINIMUM_AGE);

      if (result === true) {
        state = 'eligible';
      } else {
        // null: not applicable in this region/platform
        state = 'not_applicable';
      }
    } catch (err: unknown) {
      const e = err as AgeSignalsError;

      if (e.type === 'BelowMinimumAge') {
        state = 'below_age';
        errorType = 'BelowMinimumAge';
        errorMessage = `User is below the minimum age of ${(e as { type: 'BelowMinimumAge'; data: { minimum_age: number } }).data.minimum_age}.`;
      } else if (e.type === 'NetworkError') {
        state = 'error';
        errorType = 'NetworkError';
        errorMessage = `Network error — check your connection and try again.`;
      } else if (e.type === 'PlayStoreNotFound') {
        state = 'error';
        errorType = 'PlayStoreNotFound';
        errorMessage = 'Google Play Store is not installed on this device.';
      } else if (e.type === 'AppNotOwned') {
        state = 'error';
        errorType = 'AppNotOwned';
        errorMessage = 'This app was not installed via the Play Store. Age Signals requires Play Store installation.';
      } else if (e.type === 'ApiNotAvailable') {
        state = 'error';
        errorType = 'ApiNotAvailable';
        errorMessage = 'Age Signals API is not available. Please update Google Play Services.';
      } else if (e.type === 'InvalidRequest') {
        state = 'error';
        errorType = 'InvalidRequest';
        errorMessage = 'Invalid age check configuration.';
      } else {
        state = 'error';
        errorType = 'InternalError';
        errorMessage = String(err);
      }
    }
  }

  function reset() {
    state = 'idle';
    errorMessage = '';
    errorType = '';
  }
</script>

<main class="container">
  <h1>Age Signals Demo</h1>
  <p class="subtitle">Checking eligibility for {MINIMUM_AGE}+ content</p>

  <div class="card status-card {statusClass}">
    <div class="status-icon">
      {#if state === 'eligible'}
        ✅
      {:else if state === 'not_applicable'}
        ⚪
      {:else if state === 'below_age'}
        ❌
      {:else if state === 'error'}
        ⚠️
      {:else if state === 'checking'}
        🔄
      {:else}
        🔍
      {/if}
    </div>
    <div class="status-text">{statusText}</div>

    {#if state === 'eligible'}
      <p class="status-detail">
        The user meets the {MINIMUM_AGE}+ age requirement. Age-appropriate content can be enabled.
      </p>
    {:else if state === 'not_applicable'}
      <p class="status-detail">
        Age verification is not available in this region or on this platform.
        Apply your default content restrictions.
      </p>
    {:else if state === 'below_age'}
      <p class="status-detail">
        {errorMessage || `The user does not meet the ${MINIMUM_AGE}+ age requirement.`}
        Restrict access to age-gated content.
      </p>
    {:else if state === 'error'}
      <p class="status-detail error-detail">
        <strong>{errorType}</strong>: {errorMessage}
      </p>
    {:else if state === 'idle'}
      <p class="status-detail">
        Press the button below to check whether the current user meets the {MINIMUM_AGE}+ requirement.
      </p>
    {/if}
  </div>

  <div class="actions">
    <button
      onclick={performAgeCheck}
      disabled={isLoading}
      class="btn btn-primary"
    >
      {#if isLoading}
        Checking...
      {:else}
        Check Age ({MINIMUM_AGE}+)
      {/if}
    </button>

    {#if state !== 'idle' && state !== 'checking'}
      <button onclick={reset} class="btn btn-secondary">
        Reset
      </button>
    {/if}
  </div>

  <div class="info-section">
    <h3>Platform Behavior</h3>
    <table>
      <thead>
        <tr><th>Platform</th><th>Behavior</th><th>Expected Result</th></tr>
      </thead>
      <tbody>
        <tr>
          <td>Android (Play Store)</td>
          <td>Queries Play Age Signals SDK (no UI)</td>
          <td><code>true</code> / <code>null</code> / throws</td>
        </tr>
        <tr>
          <td>iOS 26+</td>
          <td>Shows native system consent sheet</td>
          <td><code>true</code> / <code>null</code> / throws</td>
        </tr>
        <tr>
          <td>Desktop / older iOS</td>
          <td>Not applicable</td>
          <td><code>null</code> (always)</td>
        </tr>
      </tbody>
    </table>

    <div class="note">
      <strong>Note (iOS):</strong> The consuming app must have the
      <code>com.apple.developer.declared-age-range</code> entitlement enabled
      in Xcode (Signing &amp; Capabilities → Declared Age Range).
    </div>

    <div class="note">
      <strong>Note (Android):</strong> Age Signals are only returned in regulated
      regions (Brazil from March 2026, Utah from May 2026, Louisiana from July 2026).
      In other regions, the result is <code>null</code> (not applicable).
    </div>
  </div>
</main>

<style>
  .container {
    max-width: 700px;
    margin: 0 auto;
    padding: 24px;
    font-family: system-ui, sans-serif;
  }

  h1 {
    text-align: center;
    margin-bottom: 4px;
  }

  .subtitle {
    text-align: center;
    color: #666;
    margin-top: 0;
    margin-bottom: 32px;
  }

  .card {
    border-radius: 12px;
    padding: 24px;
    margin-bottom: 24px;
    border: 2px solid transparent;
    text-align: center;
  }

  .status-icon {
    font-size: 48px;
    margin-bottom: 12px;
  }

  .status-text {
    font-size: 22px;
    font-weight: bold;
    margin-bottom: 12px;
  }

  .status-detail {
    color: inherit;
    opacity: 0.85;
    margin: 0;
    font-size: 15px;
    line-height: 1.5;
  }

  .error-detail {
    text-align: left;
  }

  .status-idle {
    background: #f5f5f5;
    border-color: #ddd;
    color: #444;
  }

  .status-eligible {
    background: #d4edda;
    border-color: #c3e6cb;
    color: #155724;
  }

  .status-not-applicable {
    background: #e9ecef;
    border-color: #ced4da;
    color: #495057;
  }

  .status-below-age {
    background: #f8d7da;
    border-color: #f5c6cb;
    color: #721c24;
  }

  .status-error {
    background: #fff3cd;
    border-color: #ffeaa7;
    color: #856404;
  }

  .actions {
    display: flex;
    gap: 12px;
    justify-content: center;
    margin-bottom: 36px;
  }

  .btn {
    padding: 12px 28px;
    border: none;
    border-radius: 8px;
    cursor: pointer;
    font-size: 16px;
    font-weight: 600;
    transition: background-color 0.15s;
  }

  .btn:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }

  .btn-primary {
    background: #0066cc;
    color: white;
  }

  .btn-primary:hover:not(:disabled) {
    background: #0052a3;
  }

  .btn-secondary {
    background: #6c757d;
    color: white;
  }

  .btn-secondary:hover:not(:disabled) {
    background: #545b62;
  }

  .info-section {
    border-top: 1px solid #e0e0e0;
    padding-top: 24px;
  }

  .info-section h3 {
    margin-top: 0;
  }

  table {
    width: 100%;
    border-collapse: collapse;
    margin-bottom: 16px;
    font-size: 14px;
  }

  th, td {
    border: 1px solid #ddd;
    padding: 8px 12px;
    text-align: left;
  }

  th {
    background: #f5f5f5;
    font-weight: 600;
  }

  code {
    background: #f0f0f0;
    padding: 1px 5px;
    border-radius: 3px;
    font-family: monospace;
    font-size: 13px;
  }

  .note {
    background: #f8f9fa;
    border-left: 4px solid #0066cc;
    padding: 10px 14px;
    margin-top: 12px;
    font-size: 13px;
    border-radius: 0 6px 6px 0;
    color: #333;
    line-height: 1.5;
  }
</style>

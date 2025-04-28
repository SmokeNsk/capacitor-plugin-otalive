export interface OtaLiveUpdaterPlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
}

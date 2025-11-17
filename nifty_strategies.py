"""Utility functions for downloading NIFTY50 data and building simple trading strategies.

This module is designed to be imported or copied into a Jupyter notebook.
"""

from __future__ import annotations

import pandas as pd
import yfinance as yf

# ---------------------------------------------------------------------------
# Configuration constants
# ---------------------------------------------------------------------------
NIFTY_TICKER = "^NSEI"  # Verify the correct ticker symbol on Yahoo Finance.
START_DATE = "2015-01-01"
END_DATE = "2025-01-01"
MA_SHORT_WINDOW = 50
MA_LONG_WINDOW = 200
MEAN_REVERSION_THRESHOLD = 0.01  # 1% drop triggers the mean reversion trade.
ATR_WINDOW = 14
ATR_BREAKOUT_K = 0.5
DOW_SELECTED_DAYS = (4,)  # Go long on Fridays by default (0=Monday, 4=Friday).


def download_price_data(
    ticker: str = NIFTY_TICKER,
    start: str = START_DATE,
    end: str = END_DATE,
) -> pd.DataFrame:
    """Download daily OHLC data for the requested ticker.

    Parameters
    ----------
    ticker : str
        Yahoo Finance ticker symbol.
    start : str
        Start date in YYYY-MM-DD format.
    end : str
        End date in YYYY-MM-DD format.

    Returns
    -------
    pd.DataFrame
        Dataframe with columns ["Adj Close", "High", "Low", "Close"].
    """
    raw_df = yf.download(ticker, start=start, end=end, progress=False)
    return raw_df[["Adj Close", "High", "Low", "Close"]].dropna()


def compute_daily_returns(adj_close: pd.Series) -> pd.Series:
    """Compute simple daily returns from adjusted close prices."""
    daily_returns = adj_close.pct_change().fillna(0.0)
    daily_returns.name = "daily_return"
    return daily_returns


def s1_buy_hold(daily_returns: pd.Series) -> pd.Series:
    """Strategy 1: Buy and hold the index by taking raw daily returns."""
    return daily_returns.rename("s1_buy_hold")


def s2_ma_trend(raw_df: pd.DataFrame, daily_returns: pd.Series) -> pd.Series:
    """Strategy 2: Moving-average crossover trend-following strategy."""
    ma_short = raw_df["Adj Close"].rolling(MA_SHORT_WINDOW).mean()
    ma_long = raw_df["Adj Close"].rolling(MA_LONG_WINDOW).mean()

    signal = (ma_short > ma_long).astype(float).shift(1).fillna(0.0)
    strategy_returns = signal * daily_returns
    return strategy_returns.rename("s2_ma_trend")


def s3_mean_reversion(
    daily_returns: pd.Series, threshold: float = MEAN_REVERSION_THRESHOLD
) -> pd.Series:
    """Strategy 3: Mean reversion after large down days."""
    signal = (daily_returns < -threshold).astype(float).shift(1).fillna(0.0)
    strategy_returns = signal * daily_returns
    return strategy_returns.rename("s3_mean_rev")


def _true_range(raw_df: pd.DataFrame) -> pd.Series:
    """Helper to compute the True Range series for ATR."""
    high_low = raw_df["High"] - raw_df["Low"]
    high_close = (raw_df["High"] - raw_df["Close"].shift(1)).abs()
    low_close = (raw_df["Low"] - raw_df["Close"].shift(1)).abs()
    true_range = pd.concat([high_low, high_close, low_close], axis=1).max(axis=1)
    return true_range


def s4_atr_breakout(
    raw_df: pd.DataFrame,
    daily_returns: pd.Series,
    atr_window: int = ATR_WINDOW,
    atr_k: float = ATR_BREAKOUT_K,
) -> pd.Series:
    """Strategy 4: ATR-based volatility breakout strategy."""
    true_range = _true_range(raw_df)
    atr = true_range.rolling(atr_window).mean()
    breakout_level = raw_df["High"].shift(1) + atr.shift(1) * atr_k

    breakout_signal = (raw_df["Close"] > breakout_level).astype(float)
    signal = breakout_signal.shift(1).fillna(0.0)
    strategy_returns = signal * daily_returns
    return strategy_returns.rename("s4_atr_breakout")


def s5_day_of_week(
    raw_df: pd.DataFrame,
    daily_returns: pd.Series,
    weekdays: tuple[int, ...] = DOW_SELECTED_DAYS,
) -> pd.Series:
    """Strategy 5: Trade only on selected weekdays (seasonality effect)."""
    dow = pd.Series(raw_df.index.dayofweek, index=raw_df.index)
    allowed_day = dow.isin(weekdays).astype(float)
    signal = allowed_day.shift(1).fillna(0.0)
    strategy_returns = signal * daily_returns
    return strategy_returns.rename("s5_dow")


def build_all_strategies(raw_df: pd.DataFrame) -> pd.DataFrame:
    """Construct all strategy return series from the raw OHLC dataframe."""
    daily_returns = compute_daily_returns(raw_df["Adj Close"])

    strategies = [
        s1_buy_hold(daily_returns),
        s2_ma_trend(raw_df, daily_returns),
        s3_mean_reversion(daily_returns),
        s4_atr_breakout(raw_df, daily_returns),
        s5_day_of_week(raw_df, daily_returns),
    ]

    strategy_df = pd.concat(strategies, axis=1).fillna(0.0)
    return strategy_df


if __name__ == "__main__":
    # Example usage: download data and build the strategy return DataFrame.
    prices = download_price_data()
    strategy_returns = build_all_strategies(prices)
    print(strategy_returns.tail())

"""Tests for CLI utility functions."""

from __future__ import annotations

import pytest

from dli.commands.utils import parse_params


class TestParseParams:
    """Tests for parse_params function."""

    def test_simple_string(self) -> None:
        """Test parsing simple string values."""
        result = parse_params(["name=hello"])
        assert result == {"name": "hello"}

    def test_date_string(self) -> None:
        """Test parsing date-like strings (kept as strings)."""
        result = parse_params(["date=2024-01-01"])
        assert result == {"date": "2024-01-01"}

    def test_integer(self) -> None:
        """Test parsing integer values."""
        result = parse_params(["count=100"])
        assert result == {"count": 100}

    def test_zero(self) -> None:
        """Test parsing zero value."""
        result = parse_params(["value=0"])
        assert result == {"value": 0}

    def test_float(self) -> None:
        """Test parsing float values."""
        result = parse_params(["rate=0.5"])
        assert result == {"rate": 0.5}

    def test_float_with_integer_part(self) -> None:
        """Test parsing float with integer part."""
        result = parse_params(["rate=3.14"])
        assert result == {"rate": 3.14}

    def test_boolean_true(self) -> None:
        """Test parsing boolean true."""
        result = parse_params(["active=true"])
        assert result == {"active": True}

    def test_boolean_false(self) -> None:
        """Test parsing boolean false."""
        result = parse_params(["active=false"])
        assert result == {"active": False}

    def test_boolean_case_insensitive(self) -> None:
        """Test boolean parsing is case insensitive."""
        result = parse_params(["flag=TRUE", "other=False"])
        assert result == {"flag": True, "other": False}

    def test_list(self) -> None:
        """Test parsing comma-separated values as list."""
        result = parse_params(["tags=a,b,c"])
        assert result == {"tags": ["a", "b", "c"]}

    def test_list_with_spaces(self) -> None:
        """Test parsing list with spaces around values."""
        result = parse_params(["tags= a , b , c "])
        assert result == {"tags": ["a", "b", "c"]}

    def test_multiple_params(self) -> None:
        """Test parsing multiple parameters."""
        result = parse_params(["start=2024-01-01", "limit=10"])
        assert result == {"start": "2024-01-01", "limit": 10}

    def test_mixed_types(self) -> None:
        """Test parsing parameters with mixed types."""
        result = parse_params([
            "date=2024-01-01",
            "count=100",
            "rate=0.5",
            "active=true",
            "tags=a,b,c",
        ])
        assert result == {
            "date": "2024-01-01",
            "count": 100,
            "rate": 0.5,
            "active": True,
            "tags": ["a", "b", "c"],
        }

    def test_invalid_format_no_equals(self) -> None:
        """Test error on missing equals sign."""
        with pytest.raises(ValueError, match="Invalid format"):
            parse_params(["invalid"])

    def test_invalid_format_empty_key(self) -> None:
        """Test error on empty key."""
        with pytest.raises(ValueError, match="Empty key"):
            parse_params(["=value"])

    def test_value_with_equals(self) -> None:
        """Test value containing equals sign."""
        result = parse_params(["expr=a=b"])
        assert result == {"expr": "a=b"}

    def test_empty_value(self) -> None:
        """Test empty value."""
        result = parse_params(["empty="])
        assert result == {"empty": ""}

    def test_empty_list(self) -> None:
        """Test parsing empty parameter list."""
        result = parse_params([])
        assert result == {}

    def test_whitespace_in_key_value(self) -> None:
        """Test whitespace trimming."""
        result = parse_params(["  key  =  value  "])
        assert result == {"key": "value"}

    def test_numeric_string_with_leading_zero(self) -> None:
        """Test string that looks numeric but has leading zeros."""
        result = parse_params(["code=007"])
        assert result == {"code": 7}  # Parsed as integer

    def test_string_not_confused_with_float(self) -> None:
        """Test string with multiple dots is not parsed as float."""
        result = parse_params(["version=1.2.3"])
        assert result == {"version": "1.2.3"}

    def test_negative_number_stays_string(self) -> None:
        """Test negative numbers (isdigit returns False for negatives)."""
        result = parse_params(["delta=-5"])
        assert result == {"delta": "-5"}  # Stays as string

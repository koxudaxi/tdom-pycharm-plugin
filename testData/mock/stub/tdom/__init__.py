from typing import Any
from tdom.processor import html as _html_type


class Node:
    pass


class Element(Node):
    pass


class Text(Node):
    pass


class Fragment(Node):
    pass


class Comment(Node):
    pass


class DocumentType(Node):
    pass


class VDOMNode:
    """Virtual DOM Node - the return type of html()"""
    pass


class Markup(str):
    def __html__(self) -> str:
        return self


class Template:
    pass


def html(template: Any) -> _html_type:
    """Process a t-string template and return an html node."""
    pass


class h:
    @staticmethod
    def html(template: Any) -> _html_type:
        """Process a t-string template and return an html node."""
        pass
